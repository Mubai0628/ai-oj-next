package com.aioj.next.ai.domain;

import com.aioj.next.ai.persistence.entity.ProblemDraftEntity;
import com.aioj.next.ai.persistence.mapper.ProblemDraftMapper;
import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.contract.ai.ProblemDraftApprovalRequest;
import com.aioj.next.contract.ai.ProblemDraftRequest;
import com.aioj.next.contract.ai.ProblemDraftResponse;
import com.aioj.next.contract.problem.TestCaseDto;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProblemDraftStore {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final int DEFAULT_TIME_LIMIT_MILLIS = 1000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 262144;

    private final AiProvider aiProvider;
    private final AiQuotaService aiQuotaService;
    private final ProblemDraftMapper problemDraftMapper;
    private final ProblemServiceClient problemServiceClient;
    private final ObjectMapper objectMapper;

    public ProblemDraftStore(
            AiProvider aiProvider,
            AiQuotaService aiQuotaService,
            ProblemDraftMapper problemDraftMapper,
            ProblemServiceClient problemServiceClient,
            ObjectMapper objectMapper
    ) {
        this.aiProvider = aiProvider;
        this.aiQuotaService = aiQuotaService;
        this.problemDraftMapper = problemDraftMapper;
        this.problemServiceClient = problemServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProblemDraftResponse generate(Long userId, ProblemDraftRequest request) {
        aiQuotaService.assertAvailable(userId);
        Long id = IdWorker.getId();
        ProblemDraftResponse generated = null;
        try {
            generated = aiProvider.generateProblemDraft(id, request);
            ProblemDraftResponse raw = generated == null ? emptyDraft(id, request) : generated;
            List<String> validationErrors = new ArrayList<>();
            if (raw.validationErrors() != null) {
                validationErrors.addAll(raw.validationErrors());
            }
            validationErrors.addAll(validate(raw));
            String validationStatus = validationErrors.isEmpty() ? "VALID" : "INVALID";
            ProblemDraftResponse response = new ProblemDraftResponse(
                    id,
                    nonBlank(raw.title(), fallbackTitle(request)),
                    nonBlank(raw.difficulty(), fallbackDifficulty(request)),
                    raw.statement(),
                    raw.tags() == null ? List.of() : raw.tags(),
                    validationStatus,
                    validationErrors,
                    normalizeTestCases(raw.testCases()),
                    limitOrDefault(raw.timeLimitMillis(), DEFAULT_TIME_LIMIT_MILLIS),
                    limitOrDefault(raw.memoryLimitKb(), DEFAULT_MEMORY_LIMIT_KB),
                    raw.importedProblemId(),
                    nonBlank(raw.model(), aiProvider.model()),
                    Math.max(0, raw.promptTokens()),
                    Math.max(0, raw.completionTokens()),
                    raw.createdAt() == null ? Instant.now() : raw.createdAt()
            );
            persist(userId, response);
            aiQuotaService.record(
                    userId,
                    aiProvider.providerName(),
                    response.model(),
                    response.promptTokens(),
                    response.completionTokens(),
                    true
            );
            return response;
        } catch (RuntimeException ex) {
            long promptTokens = generated == null ? 0 : generated.promptTokens();
            long completionTokens = generated == null ? 0 : generated.completionTokens();
            aiQuotaService.record(userId, aiProvider.providerName(), aiProvider.model(), promptTokens, completionTokens, false);
            throw ex;
        }
    }

    public PageResponse<ProblemDraftResponse> list(long page, long pageSize, String status) {
        long current = Math.max(1, page);
        long size = Math.min(Math.max(1, pageSize), 100);
        long offset = (current - 1) * size;
        QueryWrapper<ProblemDraftEntity> countQuery = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            countQuery.eq("status", status);
        }
        long total = problemDraftMapper.selectCount(countQuery);

        QueryWrapper<ProblemDraftEntity> pageQuery = new QueryWrapper<ProblemDraftEntity>()
                .orderByDesc("created_at")
                .last("LIMIT " + size + " OFFSET " + offset);
        if (status != null && !status.isBlank()) {
            pageQuery.eq("status", status);
        }
        List<ProblemDraftResponse> records = problemDraftMapper.selectList(pageQuery)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, current, size);
    }

    @Transactional
    public ProblemDraftResponse approve(Long id, Long reviewerUserId, ProblemDraftApprovalRequest request, String authorization) {
        ProblemDraftEntity draft = problemDraftMapper.selectById(id);
        if (draft == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Problem draft not found");
        }
        boolean importProblem = request != null && Boolean.TRUE.equals(request.importProblem());
        if (importProblem && draft.getImportedProblemId() == null) {
            Long problemId = problemServiceClient.createProblem(toResponse(draft), authorization);
            draft.setImportedProblemId(problemId);
        }
        draft.setStatus(STATUS_APPROVED);
        draft.setReviewedAt(LocalDateTime.now());
        draft.setReviewedBy(reviewerUserId);
        problemDraftMapper.updateById(draft);
        return toResponse(draft);
    }

    private void persist(Long userId, ProblemDraftResponse response) {
        ProblemDraftEntity entity = new ProblemDraftEntity();
        entity.setId(response.id());
        entity.setCreatorUserId(userId);
        entity.setTitle(response.title());
        entity.setDifficulty(response.difficulty());
        entity.setDraftJson(toJson(new DraftPayload(
                response.statement(),
                response.tags(),
                response.testCases(),
                response.timeLimitMillis(),
                response.memoryLimitKb(),
                response.promptTokens(),
                response.completionTokens()
        )));
        entity.setValidationStatus(response.validationStatus());
        entity.setValidationErrors(toJson(response.validationErrors()));
        entity.setModel(response.model());
        entity.setStatus(STATUS_PENDING_REVIEW);
        entity.setImportedProblemId(response.importedProblemId());
        entity.setCreatedAt(LocalDateTime.now());
        problemDraftMapper.insert(entity);
    }

    private ProblemDraftResponse toResponse(ProblemDraftEntity entity) {
        DraftPayload payload = readPayload(entity.getDraftJson());
        return new ProblemDraftResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDifficulty(),
                payload.statement(),
                payload.tags(),
                entity.getValidationStatus(),
                readErrors(entity.getValidationErrors()),
                normalizeTestCases(payload.testCases()),
                limitOrDefault(payload.timeLimitMillis(), DEFAULT_TIME_LIMIT_MILLIS),
                limitOrDefault(payload.memoryLimitKb(), DEFAULT_MEMORY_LIMIT_KB),
                entity.getImportedProblemId(),
                entity.getModel(),
                payload.promptTokens(),
                payload.completionTokens(),
                entity.getCreatedAt().atZone(ZONE).toInstant()
        );
    }

    private List<String> validate(ProblemDraftResponse response) {
        List<String> errors = new ArrayList<>();
        if (response == null) {
            errors.add("Provider returned no draft");
            return errors;
        }
        if (response.title() == null || response.title().isBlank()) {
            errors.add("title is required");
        }
        if (response.difficulty() == null || response.difficulty().isBlank()) {
            errors.add("difficulty is required");
        }
        if (response.statement() == null || response.statement().isBlank()) {
            errors.add("statement is required");
        }
        if (response.tags() == null) {
            errors.add("tags must be an array");
        }
        if (response.testCases() == null || response.testCases().isEmpty()) {
            errors.add("testCases must include at least one case");
        }
        return errors;
    }

    private DraftPayload readPayload(String json) {
        try {
            DraftPayload payload = objectMapper.readValue(json, DraftPayload.class);
            return new DraftPayload(
                    payload.statement(),
                    payload.tags() == null ? List.of() : payload.tags(),
                    normalizeTestCases(payload.testCases()),
                    limitOrDefault(payload.timeLimitMillis(), DEFAULT_TIME_LIMIT_MILLIS),
                    limitOrDefault(payload.memoryLimitKb(), DEFAULT_MEMORY_LIMIT_KB),
                    Math.max(0, payload.promptTokens()),
                    Math.max(0, payload.completionTokens())
            );
        } catch (Exception ex) {
            return new DraftPayload("", List.of(), List.of(), DEFAULT_TIME_LIMIT_MILLIS, DEFAULT_MEMORY_LIMIT_KB, 0, 0);
        }
    }

    private List<String> readErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of("Unable to parse validation errors");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Unable to serialize problem draft");
        }
    }

    private String fallbackTitle(ProblemDraftRequest request) {
        return request.topic() + " practice";
    }

    private String fallbackDifficulty(ProblemDraftRequest request) {
        return request.difficulty() == null || request.difficulty().isBlank() ? "EASY" : request.difficulty();
    }

    private ProblemDraftResponse emptyDraft(Long id, ProblemDraftRequest request) {
        return new ProblemDraftResponse(
                id,
                fallbackTitle(request),
                fallbackDifficulty(request),
                "",
                List.of(),
                "INVALID",
                List.of("Provider returned no draft"),
                List.of(),
                DEFAULT_TIME_LIMIT_MILLIS,
                DEFAULT_MEMORY_LIMIT_KB,
                null,
                aiProvider.model(),
                0,
                0,
                Instant.now()
        );
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<TestCaseDto> normalizeTestCases(List<TestCaseDto> testCases) {
        return testCases == null ? List.of() : testCases;
    }

    private int limitOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private record DraftPayload(
            String statement,
            List<String> tags,
            List<TestCaseDto> testCases,
            Integer timeLimitMillis,
            Integer memoryLimitKb,
            long promptTokens,
            long completionTokens
    ) {
    }
}
