package com.aioj.next.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aioj.ai")
public class AiProperties {
    private String provider = "openai-compatible";
    private String baseUrl = "https://api.moonshot.cn/v1/chat/completions";
    private String apiKey = "";
    private String model = "moonshot-v1-32k";
    private String problemServiceUri = "http://localhost:8202";
    private long dailyLimit = 50;
    private long monthlyLimit = 1000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProblemServiceUri() {
        return problemServiceUri;
    }

    public void setProblemServiceUri(String problemServiceUri) {
        this.problemServiceUri = problemServiceUri;
    }

    public long getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(long dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public long getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(long monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }
}
