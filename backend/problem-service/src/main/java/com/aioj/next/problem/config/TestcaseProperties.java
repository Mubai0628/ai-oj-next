package com.aioj.next.problem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aioj.testcase")
public class TestcaseProperties {
    private String storageRoot = System.getProperty("user.home") + "/.ai-oj-next/testcases";
    private long maxPackageBytes = 134_217_728L;
    private int chunkSizeBytes = 4_194_304;
    private long maxUncompressedBytes = 536_870_912L;
    private int maxEntryCount = 2_000;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public long getMaxPackageBytes() {
        return maxPackageBytes;
    }

    public void setMaxPackageBytes(long maxPackageBytes) {
        this.maxPackageBytes = maxPackageBytes;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public long getMaxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    public void setMaxUncompressedBytes(long maxUncompressedBytes) {
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    public int getMaxEntryCount() {
        return maxEntryCount;
    }

    public void setMaxEntryCount(int maxEntryCount) {
        this.maxEntryCount = maxEntryCount;
    }
}
