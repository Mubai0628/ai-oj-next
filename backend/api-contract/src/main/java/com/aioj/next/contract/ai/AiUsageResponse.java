package com.aioj.next.contract.ai;

public record AiUsageResponse(long usedToday, long dailyLimit, long usedThisMonth, long monthlyLimit) {
}

