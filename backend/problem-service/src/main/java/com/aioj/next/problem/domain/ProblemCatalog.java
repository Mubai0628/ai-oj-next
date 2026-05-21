package com.aioj.next.problem.domain;

import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.common.security.SecuritySupport;
import com.aioj.next.contract.problem.Difficulty;
import com.aioj.next.contract.problem.ProblemCreateRequest;
import com.aioj.next.contract.problem.ProblemResponse;
import com.aioj.next.contract.problem.ProblemUpdateRequest;
import com.aioj.next.contract.problem.TestCaseDto;
import com.aioj.next.problem.persistence.entity.ProblemEntity;
import com.aioj.next.problem.persistence.entity.ProblemTestCaseEntity;
import com.aioj.next.problem.persistence.mapper.ProblemMapper;
import com.aioj.next.problem.persistence.mapper.ProblemTestCaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ProblemCatalog {
    private static final int DEFAULT_TIME_LIMIT_MILLIS = 1000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 262144;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ProblemMapper problemMapper;
    private final ProblemTestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper;

    public ProblemCatalog(ProblemMapper problemMapper, ProblemTestCaseMapper testCaseMapper, ObjectMapper objectMapper) {
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ProblemResponse> list(long page, long pageSize, String keyword, Difficulty difficulty, String tag) {
        Page<ProblemEntity> result = problemMapper.selectPage(new Page<>(normalizePage(page), normalizePageSize(pageSize)),
                baseProblemQuery()
                        .like(StringUtils.hasText(keyword), ProblemEntity::getTitle, keyword)
                        .eq(difficulty != null, ProblemEntity::getDifficulty, difficulty)
                        .apply(StringUtils.hasText(tag), "JSON_CONTAINS(tags, JSON_QUOTE({0}))", normalizeTag(tag))
                        .orderByDesc(ProblemEntity::getCreatedAt)
                        .orderByDesc(ProblemEntity::getId));
        List<ProblemResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ProblemResponse get(Long id) {
        return toResponse(requireActiveProblem(id));
    }

    public boolean existsActive(Long id) {
        return problemMapper.selectCount(baseProblemQuery().eq(ProblemEntity::getId, id)) > 0;
    }

    @Transactional
    public ProblemResponse create(ProblemCreateRequest request, boolean aiGenerated) {
        Instant now = Instant.now();
        ProblemEntity problem = new ProblemEntity();
        apply(problem, request.title(), request.difficulty(), request.statement(), request.tags(),
                request.timeLimitMillis(), request.memoryLimitKb());
        problem.setAiGenerated(aiGenerated);
        problem.setCreatedBy(SecuritySupport.currentUserId());
        problem.setCreatedAt(now);
        problem.setUpdatedAt(now);
        problem.setDeleted(false);
        problemMapper.insert(problem);
        replaceTestCases(problem.getId(), request.testCases());
        return toResponse(problem);
    }

    @Transactional
    public ProblemResponse update(Long id, ProblemUpdateRequest request) {
        ProblemEntity problem = requireActiveProblem(id);
        apply(problem, request.title(), request.difficulty(), request.statement(), request.tags(),
                request.timeLimitMillis(), request.memoryLimitKb());
        problem.setUpdatedAt(Instant.now());
        problemMapper.updateById(problem);
        replaceTestCases(problem.getId(), request.testCases());
        return toResponse(problem);
    }

    @Transactional
    public void delete(Long id) {
        ProblemEntity problem = requireActiveProblem(id);
        problem.setDeleted(true);
        problem.setUpdatedAt(Instant.now());
        problemMapper.updateById(problem);
    }

    private ProblemEntity requireActiveProblem(Long id) {
        ProblemEntity problem = problemMapper.selectOne(baseProblemQuery().eq(ProblemEntity::getId, id));
        if (problem == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Problem not found");
        }
        return problem;
    }

    private LambdaQueryWrapper<ProblemEntity> baseProblemQuery() {
        return new LambdaQueryWrapper<ProblemEntity>().eq(ProblemEntity::getDeleted, false);
    }

    private void apply(ProblemEntity problem, String title, Difficulty difficulty, String statement, List<String> tags,
                       int timeLimitMillis, int memoryLimitKb) {
        problem.setTitle(title);
        problem.setDifficulty(difficulty);
        problem.setStatement(statement);
        problem.setTags(toJson(tags == null ? List.of() : tags));
        problem.setTimeLimitMillis(timeLimitMillis <= 0 ? DEFAULT_TIME_LIMIT_MILLIS : timeLimitMillis);
        problem.setMemoryLimitKb(memoryLimitKb <= 0 ? DEFAULT_MEMORY_LIMIT_KB : memoryLimitKb);
    }

    private void replaceTestCases(Long problemId, List<TestCaseDto> testCases) {
        testCaseMapper.delete(new LambdaQueryWrapper<ProblemTestCaseEntity>()
                .eq(ProblemTestCaseEntity::getProblemId, problemId));
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseDto testCase = testCases.get(i);
            ProblemTestCaseEntity entity = new ProblemTestCaseEntity();
            entity.setProblemId(problemId);
            entity.setInput(testCase.input());
            entity.setExpectedOutput(testCase.expectedOutput());
            entity.setSample(testCase.sample());
            entity.setSortOrder(i);
            testCaseMapper.insert(entity);
        }
    }

    private ProblemResponse toResponse(ProblemEntity problem) {
        List<TestCaseDto> samples = testCaseMapper.selectList(new LambdaQueryWrapper<ProblemTestCaseEntity>()
                        .eq(ProblemTestCaseEntity::getProblemId, problem.getId())
                        .eq(ProblemTestCaseEntity::getSample, true)
                        .orderByAsc(ProblemTestCaseEntity::getSortOrder)
                        .orderByAsc(ProblemTestCaseEntity::getId))
                .stream()
                .map(testCase -> new TestCaseDto(testCase.getInput(), testCase.getExpectedOutput(),
                        Boolean.TRUE.equals(testCase.getSample())))
                .toList();
        return new ProblemResponse(problem.getId(), problem.getTitle(), problem.getDifficulty(), problem.getStatement(),
                fromJson(problem.getTags()), samples, problem.getTimeLimitMillis(), problem.getMemoryLimitKb(),
                Boolean.TRUE.equals(problem.getAiGenerated()), problem.getCreatedAt());
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Invalid problem tags");
        }
    }

    private List<String> fromJson(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tags, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Problem tags are not valid JSON");
        }
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

    private String normalizeTag(String tag) {
        return tag == null ? null : tag.trim();
    }
}
