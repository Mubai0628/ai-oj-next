package com.aioj.next.ai.domain;

import com.aioj.next.ai.config.AiProperties;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.contract.ai.ProblemDraftResponse;
import com.aioj.next.contract.problem.Difficulty;
import com.aioj.next.contract.problem.ProblemCreateRequest;
import com.aioj.next.contract.problem.TestCaseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class ProblemServiceClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProblemServiceClient(AiProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.getProblemServiceUri()))
                .build();
        this.objectMapper = objectMapper;
    }

    public Long createProblem(ProblemDraftResponse draft, String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Authorization Bearer token is required to import draft");
        }
        List<TestCaseDto> testCases = draft.testCases() == null ? List.of() : draft.testCases();
        if (testCases.isEmpty()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Problem draft has no test cases to import");
        }
        ProblemCreateRequest request = new ProblemCreateRequest(
                draft.title(),
                parseDifficulty(draft.difficulty()),
                draft.statement(),
                draft.tags() == null ? List.of() : draft.tags(),
                testCases,
                limitOrDefault(draft.timeLimitMillis(), 1000),
                limitOrDefault(draft.memoryLimitKb(), 262144),
                null
        );

        try {
            String response = restClient.post()
                    .uri("/problems")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return parseProblemId(response);
        } catch (RestClientResponseException ex) {
            ErrorCode code = ex.getStatusCode().is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
            throw new DomainException(code, "Problem import failed: " + summarize(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Problem import failed: " + ex.getMessage());
        }
    }

    private Long parseProblemId(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.has("code") ? root.get("code").asInt() : 0;
            if (code != 0) {
                String message = root.has("message") ? root.get("message").asText() : "problem-service rejected request";
                throw new DomainException(ErrorCode.BAD_REQUEST, "Problem import failed: " + message);
            }
            JsonNode data = root.has("data") ? root.get("data") : root;
            JsonNode id = data == null ? null : data.get("id");
            if (id == null || !id.canConvertToLong()) {
                throw new DomainException(ErrorCode.INTERNAL_ERROR, "Problem import response did not include problem id");
            }
            return id.asLong();
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Problem import response could not be parsed");
        }
    }

    private Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Unsupported draft difficulty: " + value);
        }
    }

    private int limitOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "empty response body";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8202";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
