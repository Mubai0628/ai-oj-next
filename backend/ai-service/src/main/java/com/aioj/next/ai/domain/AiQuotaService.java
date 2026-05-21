package com.aioj.next.ai.domain;

import com.aioj.next.ai.config.AiProperties;
import com.aioj.next.ai.persistence.entity.AiQuotaPolicyEntity;
import com.aioj.next.ai.persistence.entity.AiUsageRecordEntity;
import com.aioj.next.ai.persistence.mapper.AiQuotaPolicyMapper;
import com.aioj.next.ai.persistence.mapper.AiUsageRecordMapper;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.contract.ai.AiUsageResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AiQuotaService {
    private final AiProperties properties;
    private final AiQuotaPolicyMapper quotaPolicyMapper;
    private final AiUsageRecordMapper usageRecordMapper;

    public AiQuotaService(AiProperties properties, AiQuotaPolicyMapper quotaPolicyMapper, AiUsageRecordMapper usageRecordMapper) {
        this.properties = properties;
        this.quotaPolicyMapper = quotaPolicyMapper;
        this.usageRecordMapper = usageRecordMapper;
    }

    public void assertAvailable(Long userId) {
        QuotaLimits limits = limitsFor(userId);
        if (countUsage(userId, startOfToday()) >= limits.dailyLimit()
                || countUsage(userId, startOfMonth()) >= limits.monthlyLimit()) {
            throw new DomainException(ErrorCode.TOO_MANY_REQUESTS, "AI quota exceeded");
        }
    }

    @Transactional
    public void record(Long userId, String provider, String model, long promptTokens, long completionTokens, boolean success) {
        AiUsageRecordEntity record = new AiUsageRecordEntity();
        record.setUserId(userId);
        record.setProvider(blankToDefault(provider, properties.getProvider()));
        record.setModel(blankToDefault(model, properties.getModel()));
        record.setPromptTokens(Math.max(0, promptTokens));
        record.setCompletionTokens(Math.max(0, completionTokens));
        record.setSuccess(success);
        record.setCreatedAt(LocalDateTime.now());
        usageRecordMapper.insert(record);
    }

    public AiUsageResponse usage(Long userId) {
        QuotaLimits limits = limitsFor(userId);
        return new AiUsageResponse(
                countUsage(userId, startOfToday()),
                limits.dailyLimit(),
                countUsage(userId, startOfMonth()),
                limits.monthlyLimit()
        );
    }

    private QuotaLimits limitsFor(Long userId) {
        AiQuotaPolicyEntity userPolicy = quotaPolicyMapper.selectOne(new QueryWrapper<AiQuotaPolicyEntity>()
                .eq("enabled", true)
                .eq("scope_type", "USER")
                .eq("scope_id", userId)
                .last("LIMIT 1"));
        if (userPolicy != null) {
            return new QuotaLimits(userPolicy.getDailyLimit(), userPolicy.getMonthlyLimit());
        }
        AiQuotaPolicyEntity globalPolicy = quotaPolicyMapper.selectOne(new QueryWrapper<AiQuotaPolicyEntity>()
                .eq("enabled", true)
                .eq("scope_type", "GLOBAL")
                .isNull("scope_id")
                .last("LIMIT 1"));
        if (globalPolicy != null) {
            return new QuotaLimits(globalPolicy.getDailyLimit(), globalPolicy.getMonthlyLimit());
        }
        return new QuotaLimits(properties.getDailyLimit(), properties.getMonthlyLimit());
    }

    private long countUsage(Long userId, LocalDateTime start) {
        return usageRecordMapper.selectCount(new QueryWrapper<AiUsageRecordEntity>()
                .eq("user_id", userId)
                .ge("created_at", start));
    }

    private LocalDateTime startOfToday() {
        return LocalDateTime.now().toLocalDate().atStartOfDay();
    }

    private LocalDateTime startOfMonth() {
        return LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record QuotaLimits(long dailyLimit, long monthlyLimit) {
    }
}
