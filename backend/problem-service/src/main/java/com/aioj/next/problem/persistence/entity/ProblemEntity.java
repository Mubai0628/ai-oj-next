package com.aioj.next.problem.persistence.entity;

import com.aioj.next.contract.problem.Difficulty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("problems")
public class ProblemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private Difficulty difficulty;
    private String statement;
    private String tags;
    private Integer timeLimitMillis;
    private Integer memoryLimitKb;
    private Boolean aiGenerated;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getTimeLimitMillis() {
        return timeLimitMillis;
    }

    public void setTimeLimitMillis(Integer timeLimitMillis) {
        this.timeLimitMillis = timeLimitMillis;
    }

    public Integer getMemoryLimitKb() {
        return memoryLimitKb;
    }

    public void setMemoryLimitKb(Integer memoryLimitKb) {
        this.memoryLimitKb = memoryLimitKb;
    }

    public Boolean getAiGenerated() {
        return aiGenerated;
    }

    public void setAiGenerated(Boolean aiGenerated) {
        this.aiGenerated = aiGenerated;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
