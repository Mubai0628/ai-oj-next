package com.aioj.next.problem.domain;

import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.api.TraceIds;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.common.security.Role;
import com.aioj.next.common.security.SecuritySupport;
import com.aioj.next.contract.judge.JudgeTaskMessage;
import com.aioj.next.contract.submission.SubmissionCreateRequest;
import com.aioj.next.contract.submission.SubmissionResponse;
import com.aioj.next.contract.submission.SubmissionStatus;
import com.aioj.next.problem.config.JudgeQueueConfig;
import com.aioj.next.problem.persistence.entity.SubmissionEntity;
import com.aioj.next.problem.persistence.mapper.SubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("java", "cpp", "python");

    private final ProblemCatalog problemCatalog;
    private final SubmissionMapper submissionMapper;
    private final RabbitTemplate rabbitTemplate;

    public SubmissionService(ProblemCatalog problemCatalog, SubmissionMapper submissionMapper, RabbitTemplate rabbitTemplate) {
        this.problemCatalog = problemCatalog;
        this.submissionMapper = submissionMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public SubmissionResponse submit(SubmissionCreateRequest request) {
        String language = normalizeLanguage(request.language());
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Unsupported language: " + request.language());
        }
        if (!problemCatalog.existsActive(request.problemId())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Problem not found");
        }

        Long userId = SecuritySupport.currentUserId();
        Instant now = Instant.now();
        SubmissionEntity submission = new SubmissionEntity();
        submission.setProblemId(request.problemId());
        submission.setUserId(userId);
        submission.setLanguage(language);
        submission.setCode(request.code());
        submission.setStatus(SubmissionStatus.QUEUED);
        submission.setJudgeMessage("Queued for judging");
        submission.setRetryCount(0);
        submission.setCreatedAt(now);
        submission.setUpdatedAt(now);
        submissionMapper.insert(submission);

        publishAfterCommit(new JudgeTaskMessage(submission.getId(), request.problemId(), userId, language, TraceIds.current()));
        return toResponse(submission);
    }

    public SubmissionResponse get(Long id) {
        SubmissionEntity submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Submission not found");
        }
        assertCanRead(submission);
        return toResponse(submission);
    }

    public PageResponse<SubmissionResponse> list(long page, long pageSize, Long problemId, Long userId,
                                                 SubmissionStatus status, Boolean mine) {
        Long currentUserId = SecuritySupport.currentUserId();
        boolean privileged = SecuritySupport.hasAnyRole(Role.TEACHER, Role.ADMIN);
        LambdaQueryWrapper<SubmissionEntity> query = new LambdaQueryWrapper<SubmissionEntity>()
                .eq(problemId != null, SubmissionEntity::getProblemId, problemId)
                .eq(status != null, SubmissionEntity::getStatus, status)
                .orderByDesc(SubmissionEntity::getCreatedAt)
                .orderByDesc(SubmissionEntity::getId);

        if (!privileged) {
            if (userId != null && !userId.equals(currentUserId)) {
                throw new DomainException(ErrorCode.FORBIDDEN, "Cannot query other users' submissions");
            }
            query.eq(SubmissionEntity::getUserId, currentUserId);
        } else if (Boolean.TRUE.equals(mine)) {
            query.eq(SubmissionEntity::getUserId, currentUserId);
        } else {
            query.eq(userId != null, SubmissionEntity::getUserId, userId);
        }

        Page<SubmissionEntity> result = submissionMapper.selectPage(new Page<>(normalizePage(page), normalizePageSize(pageSize)), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Language is required");
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private void assertCanRead(SubmissionEntity submission) {
        if (SecuritySupport.hasAnyRole(Role.TEACHER, Role.ADMIN)) {
            return;
        }
        if (!submission.getUserId().equals(SecuritySupport.currentUserId())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Cannot read other users' submissions");
        }
    }

    private SubmissionResponse toResponse(SubmissionEntity submission) {
        return new SubmissionResponse(submission.getId(), submission.getProblemId(), submission.getUserId(),
                submission.getLanguage(), submission.getStatus(), submission.getJudgeMessage(),
                submission.getTimeMillis(), submission.getMemoryKb(), submission.getCreatedAt(), submission.getJudgedAt());
    }

    private void publishAfterCommit(JudgeTaskMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishJudgeTask(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishJudgeTask(message);
            }
        });
    }

    private void publishJudgeTask(JudgeTaskMessage message) {
        rabbitTemplate.convertAndSend(JudgeQueueConfig.JUDGE_EXCHANGE, JudgeQueueConfig.JUDGE_ROUTING_KEY, message);
        log.info("Published judge task submission={} problem={}", message.submissionId(), message.problemId());
    }

    private long normalizePage(long page) {
        return Math.max(page, 1);
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
