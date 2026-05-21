package com.aioj.next.judge.domain;

import com.aioj.next.contract.judge.JudgeTaskMessage;
import com.aioj.next.contract.submission.SubmissionStatus;
import com.aioj.next.judge.config.JudgeWorkerProperties;
import com.aioj.next.judge.persistence.entity.JudgeAuditLogEntity;
import com.aioj.next.judge.persistence.entity.SubmissionEntity;
import com.aioj.next.judge.persistence.mapper.JudgeAuditLogMapper;
import com.aioj.next.judge.persistence.mapper.SubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Objects;

@Service
public class SubmissionJudgingService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionJudgingService.class);
    private static final int MESSAGE_LIMIT = 512;

    private final SubmissionMapper submissionMapper;
    private final JudgeAuditLogMapper auditLogMapper;
    private final JudgeWorkerProperties properties;
    private final String workerId;

    public SubmissionJudgingService(SubmissionMapper submissionMapper, JudgeAuditLogMapper auditLogMapper,
                                    JudgeWorkerProperties properties) {
        this.submissionMapper = submissionMapper;
        this.auditLogMapper = auditLogMapper;
        this.properties = properties;
        this.workerId = resolveWorkerId();
    }

    public boolean startRunning(JudgeTaskMessage task) {
        validateTask(task);
        SubmissionEntity update = new SubmissionEntity();
        update.setStatus(SubmissionStatus.RUNNING);
        update.setJudgeMessage("Judging started");
        update.setUpdatedAt(Instant.now());
        int updated = submissionMapper.update(update, new LambdaUpdateWrapper<SubmissionEntity>()
                .eq(SubmissionEntity::getId, task.submissionId())
                .eq(SubmissionEntity::getProblemId, task.problemId())
                .eq(SubmissionEntity::getUserId, task.userId())
                .eq(SubmissionEntity::getLanguage, task.language())
                .eq(SubmissionEntity::getStatus, SubmissionStatus.QUEUED));
        if (updated == 1) {
            audit(task.submissionId(), SubmissionStatus.QUEUED, SubmissionStatus.RUNNING, "Judging started");
            return true;
        }
        SubmissionEntity existing = submissionMapper.selectById(task.submissionId());
        if (existing != null && existing.getStatus() == SubmissionStatus.QUEUED && !matchesTask(existing, task)) {
            throw new NonRetryableJudgeTaskException("Judge task does not match queued submission");
        }
        log.info("submission={} was not QUEUED; currentStatus={}", task.submissionId(),
                existing == null ? "missing" : existing.getStatus());
        return false;
    }

    public boolean finish(JudgeTaskMessage task, JudgeResult result) {
        SubmissionStatus status = result.status();
        Instant judgedAt = result.judgedAt() == null ? Instant.now() : result.judgedAt();
        SubmissionEntity update = new SubmissionEntity();
        update.setStatus(status);
        update.setJudgeMessage(truncate(result.message()));
        update.setTimeMillis(result.timeMillis());
        update.setMemoryKb(result.memoryKb());
        update.setJudgedAt(judgedAt);
        update.setUpdatedAt(judgedAt);
        int updated = submissionMapper.update(update, new LambdaUpdateWrapper<SubmissionEntity>()
                .eq(SubmissionEntity::getId, task.submissionId())
                .eq(SubmissionEntity::getStatus, SubmissionStatus.RUNNING));
        if (updated == 1) {
            audit(task.submissionId(), SubmissionStatus.RUNNING, status, result.message());
            return true;
        }
        log.info("submission={} terminal update skipped because it is no longer RUNNING", task.submissionId());
        return false;
    }

    public void markSystemError(Long submissionId, String message) {
        if (submissionId == null) {
            return;
        }
        Instant now = Instant.now();
        SubmissionEntity update = new SubmissionEntity();
        update.setStatus(SubmissionStatus.SYSTEM_ERROR);
        update.setJudgeMessage(truncate(message));
        update.setJudgedAt(now);
        update.setUpdatedAt(now);
        int updated = submissionMapper.update(update, new LambdaUpdateWrapper<SubmissionEntity>()
                .eq(SubmissionEntity::getId, submissionId)
                .in(SubmissionEntity::getStatus, SubmissionStatus.QUEUED, SubmissionStatus.RUNNING));
        if (updated == 1) {
            audit(submissionId, null, SubmissionStatus.SYSTEM_ERROR, message);
        }
    }

    public void validateTask(JudgeTaskMessage task) {
        if (task == null || task.submissionId() == null || task.problemId() == null || task.userId() == null) {
            throw new NonRetryableJudgeTaskException("Judge task is missing required identifiers");
        }
        if (!StringUtils.hasText(task.language()) || !properties.getLanguageWhitelist().contains(task.language())) {
            throw new NonRetryableJudgeTaskException("Judge task language is not enabled");
        }
    }

    private void audit(Long submissionId, SubmissionStatus fromStatus, SubmissionStatus toStatus, String message) {
        try {
            JudgeAuditLogEntity audit = new JudgeAuditLogEntity();
            audit.setSubmissionId(submissionId);
            audit.setFromStatus(fromStatus);
            audit.setToStatus(toStatus);
            audit.setWorkerId(workerId);
            audit.setMessage(truncate(message));
            audit.setCreatedAt(Instant.now());
            auditLogMapper.insert(audit);
        } catch (RuntimeException ex) {
            log.warn("Failed to write judge audit log for submission={}", submissionId, ex);
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, MESSAGE_LIMIT);
    }

    private boolean matchesTask(SubmissionEntity submission, JudgeTaskMessage task) {
        return Objects.equals(submission.getProblemId(), task.problemId())
                && Objects.equals(submission.getUserId(), task.userId())
                && Objects.equals(submission.getLanguage(), task.language());
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (UnknownHostException ex) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
