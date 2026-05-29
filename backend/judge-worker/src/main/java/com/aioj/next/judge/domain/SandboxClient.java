package com.aioj.next.judge.domain;

import com.aioj.next.contract.judge.JudgeTaskMessage;
import com.aioj.next.contract.submission.SubmissionStatus;
import com.aioj.next.judge.config.JudgeWorkerProperties;
import com.aioj.next.judge.persistence.entity.SubmissionEntity;
import com.aioj.next.judge.persistence.mapper.SubmissionMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SandboxClient {
    private static final Logger log = LoggerFactory.getLogger(SandboxClient.class);
    private static final int STDOUT_COLLECT_LIMIT = 64 * 1024;
    private static final int STDERR_COLLECT_LIMIT = 64 * 1024;
    private static final long DEFAULT_TIME_LIMIT_MILLIS = 1000L;
    private static final long DEFAULT_MEMORY_LIMIT_KB = 262144L;
    private static final ParameterizedTypeReference<List<SandboxRunResult>> RUN_RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final JudgeWorkerProperties properties;
    private final TestcasePackageCache testcaseCache;
    private final SubmissionMapper submissionMapper;
    private final OutputComparator outputComparator;
    private final RestClient restClient;

    public SandboxClient(JudgeWorkerProperties properties,
                         TestcasePackageCache testcaseCache,
                         SubmissionMapper submissionMapper,
                         OutputComparator outputComparator) {
        this.properties = properties;
        this.testcaseCache = testcaseCache;
        this.submissionMapper = submissionMapper;
        this.outputComparator = outputComparator;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(stripTrailingPath(properties.getSandboxEndpoint()))
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .connectTimeout(resolveTimeout(properties.getSandboxTimeout()))
                        .build()));
        if (StringUtils.hasText(properties.getSandboxToken())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getSandboxToken());
        }
        this.restClient = builder.build();
    }

    public JudgeResult judge(JudgeTaskMessage task) {
        if (!properties.getLanguageWhitelist().contains(task.language())) {
            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled",
                    0L, 0L, Instant.now(), null, null, null, null);
        }
        LangProfile lang;
        try {
            lang = LangProfile.of(task.language());
        } catch (IllegalArgumentException ex) {
            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, ex.getMessage(),
                    0L, 0L, Instant.now(), null, null, null, null);
        }

        try {
            SubmissionEntity submission = submissionMapper.selectById(task.submissionId());
            if (submission == null) {
                return JudgeResult.systemError("Submission not found: " + task.submissionId());
            }
            String sourceCode = submission.getCode();
            if (!StringUtils.hasText(sourceCode)) {
                return JudgeResult.systemError("Submission source code is empty");
            }

            Optional<PreparedTestcasePackage> preparedPackage = testcaseCache.prepareActivePackage(task.problemId());
            if (preparedPackage.isEmpty()) {
                return JudgeResult.systemError("No active testcase package");
            }
            PreparedTestcasePackage testcasePackage = preparedPackage.get();
            if (testcasePackage.cases().isEmpty()) {
                return JudgeResult.systemError("No testcase available");
            }

            long cpuLimitNs = millisToNanos(nonNullOrDefault(task.timeLimitMillis(), DEFAULT_TIME_LIMIT_MILLIS));
            long memoryLimitBytes = kbToBytes(nonNullOrDefault(task.memoryLimitKb(), DEFAULT_MEMORY_LIMIT_KB));
            String compiledFileId = null;
            try {
                if (lang.requiresCompile()) {
                    CompileOutcome compile = compileSource(lang, sourceCode, safeMultiply(cpuLimitNs, 10),
                            safeMultiply(memoryLimitBytes, 2));
                    if (compile.failed()) {
                        return new JudgeResult(SubmissionStatus.COMPILE_ERROR, compile.message(),
                                compile.timeMillis(), compile.memoryKb(), Instant.now(),
                                null, compile.stderr(), compile.exitStatus(), compile.runTimeMillis());
                    }
                    compiledFileId = compile.fileId();
                }

                long maxTimeMs = 0L;
                long maxMemoryKb = 0L;
                long maxRunTimeMs = 0L;
                for (PreparedTestcaseCase testcase : testcasePackage.cases()) {
                    CaseRunOutcome outcome = runCase(lang, sourceCode, compiledFileId, testcase, cpuLimitNs,
                            memoryLimitBytes);
                    maxTimeMs = Math.max(maxTimeMs, nullToZero(outcome.timeMillis()));
                    maxMemoryKb = Math.max(maxMemoryKb, nullToZero(outcome.memoryKb()));
                    maxRunTimeMs = Math.max(maxRunTimeMs, nullToZero(outcome.runTimeMillis()));
                    if (outcome.terminalStatus() != SubmissionStatus.ACCEPTED) {
                        return new JudgeResult(outcome.terminalStatus(), outcome.message(),
                                outcome.timeMillis(), outcome.memoryKb(), Instant.now(),
                                outcome.stdout(), outcome.stderr(), outcome.exitStatus(), outcome.runTimeMillis());
                    }
                }
                return new JudgeResult(SubmissionStatus.ACCEPTED, "Accepted",
                        maxTimeMs, maxMemoryKb, Instant.now(), null, null, null, maxRunTimeMs);
            } finally {
                if (compiledFileId != null) {
                    try {
                        deleteCachedFile(compiledFileId);
                    } catch (RuntimeException ex) {
                        log.warn("Failed to cleanup sandbox fileId={}: {}", compiledFileId, ex.getMessage());
                    }
                }
            }
        } catch (RuntimeException | IOException ex) {
            log.warn("Sandbox execution failed for submission={}", task.submissionId(), ex);
            return JudgeResult.systemError("Sandbox execution failed: " + safeMessage(ex));
        }
    }

    private CompileOutcome compileSource(LangProfile lang, String sourceCode, long cpuLimitNs, long memoryLimitBytes) {
        SandboxRunResult result = runSandbox(List.of(Map.of(
                "args", lang.compileArgs(),
                "env", lang.envVars(),
                "files", standardFiles(""),
                "cpuLimit", cpuLimitNs,
                "memoryLimit", memoryLimitBytes,
                "procLimit", 50,
                "copyIn", Map.of(lang.sourceFileName(), Map.of("content", sourceCode)),
                "copyOutCached", List.of(lang.executableName())
        )));
        Long timeMs = nanosToMillis(result.time());
        Long memoryKb = bytesToKb(result.memory());
        Long runTimeMs = nanosToMillis(result.runTime());
        String stderr = fileContent(result, "stderr");
        if (!"Accepted".equals(result.status())) {
            return CompileOutcome.failed(firstText(stderr, result.error(), result.status()), timeMs, memoryKb,
                    stderr, result.exitStatus(), runTimeMs);
        }
        String fileId = result.fileIds() == null ? null : result.fileIds().get(lang.executableName());
        if (!StringUtils.hasText(fileId)) {
            return CompileOutcome.failed("Compile succeeded but sandbox did not return cached fileId",
                    timeMs, memoryKb, stderr, result.exitStatus(), runTimeMs);
        }
        return CompileOutcome.success(fileId, timeMs, memoryKb, stderr, result.exitStatus(), runTimeMs);
    }

    private CaseRunOutcome runCase(LangProfile lang,
                                   String sourceCode,
                                   String compiledFileId,
                                   PreparedTestcaseCase testcase,
                                   long cpuLimitNs,
                                   long memoryLimitBytes) throws IOException {
        String stdin = Files.readString(testcase.inputFile(), StandardCharsets.UTF_8);
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("args", lang.runArgs());
        command.put("env", lang.envVars());
        command.put("files", standardFiles(stdin));
        command.put("cpuLimit", cpuLimitNs);
        command.put("memoryLimit", memoryLimitBytes);
        command.put("procLimit", 50);
        Map<String, Object> copyIn = new LinkedHashMap<>();
        if (lang.requiresCompile()) {
            copyIn.put(lang.executableName(), Map.of("fileId", compiledFileId));
        } else {
            copyIn.put(lang.sourceFileName(), Map.of("content", sourceCode));
        }
        command.put("copyIn", copyIn);

        SandboxRunResult result = runSandbox(List.of(command));
        Long timeMs = nanosToMillis(result.time());
        Long memoryKb = bytesToKb(result.memory());
        Long runTimeMs = nanosToMillis(result.runTime());
        String stdout = fileContent(result, "stdout");
        String stderr = fileContent(result, "stderr");
        SubmissionStatus status = mapStatus(result.status(), result.fileError());
        if (status != SubmissionStatus.ACCEPTED) {
            return new CaseRunOutcome(status, firstText(result.error(), result.status()),
                    timeMs, memoryKb, stdout, stderr, result.exitStatus(), runTimeMs);
        }
        try (var expected = Files.newInputStream(testcase.expectedOutputFile());
             var actual = new ByteArrayInputStream(nullToEmpty(stdout).getBytes(StandardCharsets.UTF_8))) {
            OutputComparator.ComparisonResult comparison = outputComparator.compare(expected, actual);
            if (!comparison.accepted()) {
                return new CaseRunOutcome(SubmissionStatus.WRONG_ANSWER, comparison.message(),
                        timeMs, memoryKb, stdout, stderr, result.exitStatus(), runTimeMs);
            }
        }
        return new CaseRunOutcome(SubmissionStatus.ACCEPTED, "Accepted",
                timeMs, memoryKb, stdout, stderr, result.exitStatus(), runTimeMs);
    }

    private SandboxRunResult runSandbox(List<Map<String, Object>> commands) {
        List<SandboxRunResult> results = restClient.post()
                .uri("/run")
                .body(Map.of("cmd", commands))
                .retrieve()
                .body(RUN_RESULT_TYPE);
        if (results == null || results.isEmpty() || results.get(0) == null) {
            throw new IllegalStateException("Sandbox returned empty /run response");
        }
        return results.get(0);
    }

    private void deleteCachedFile(String fileId) {
        restClient.delete().uri("/file/{id}", fileId).retrieve().toBodilessEntity();
    }

    private SubmissionStatus mapStatus(String goJudgeStatus, List<SandboxFileError> fileError) {
        if (fileError != null) {
            for (SandboxFileError error : fileError) {
                if ("CollectSizeExceeded".equals(error.type())) {
                    return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
                }
            }
        }
        if (goJudgeStatus == null) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        return switch (goJudgeStatus) {
            case "Accepted" -> SubmissionStatus.ACCEPTED;
            case "Time Limit Exceeded" -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
            case "Output Limit Exceeded" -> SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
            case "Nonzero Exit Status", "Signalled" -> SubmissionStatus.RUNTIME_ERROR;
            case "File Error", "Internal Error" -> SubmissionStatus.SYSTEM_ERROR;
            default -> SubmissionStatus.SYSTEM_ERROR;
        };
    }

    private List<Map<String, Object>> standardFiles(String stdin) {
        return List.of(
                Map.of("content", stdin),
                Map.of("name", "stdout", "max", STDOUT_COLLECT_LIMIT),
                Map.of("name", "stderr", "max", STDERR_COLLECT_LIMIT)
        );
    }

    private static String stripTrailingPath(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "http://localhost:8090";
        }
        String trimmed = endpoint.trim();
        if (trimmed.endsWith("/run") || trimmed.endsWith("/execute")) {
            return trimmed.substring(0, trimmed.lastIndexOf('/'));
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static Duration resolveTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(10) : timeout;
    }

    private static long nonNullOrDefault(Integer value, long fallback) {
        return value == null || value <= 0 ? fallback : value.longValue();
    }

    private static long nonNullOrDefault(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static long millisToNanos(long millis) {
        return safeMultiply(millis, 1_000_000L);
    }

    private static long kbToBytes(long kb) {
        return safeMultiply(kb, 1024L);
    }

    private static long safeMultiply(long value, long factor) {
        if (value > Long.MAX_VALUE / factor) {
            return Long.MAX_VALUE;
        }
        return value * factor;
    }

    private static Long nanosToMillis(Long nanos) {
        if (nanos == null) {
            return null;
        }
        if (nanos <= 0) {
            return 0L;
        }
        return (nanos + 999_999L) / 1_000_000L;
    }

    private static Long bytesToKb(Long bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes <= 0) {
            return 0L;
        }
        return (bytes + 1023L) / 1024L;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String fileContent(SandboxRunResult result, String name) {
        return result.files() == null ? null : result.files().get(name);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return "Sandbox execution failed";
    }

    private static String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private record LangProfile(
            boolean requiresCompile,
            List<String> compileArgs,
            String sourceFileName,
            String executableName,
            List<String> runArgs,
            List<String> envVars
    ) {
        static LangProfile of(String lang) {
            return switch (lang) {
                case "cpp" -> new LangProfile(true,
                        List.of("/usr/bin/g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"),
                        "main.cpp", "main",
                        List.of("./main"),
                        List.of("PATH=/usr/bin:/bin"));
                case "java" -> new LangProfile(true,
                        List.of("/usr/bin/javac", "Main.java"),
                        "Main.java", "Main.class",
                        List.of("/usr/bin/java", "-cp", ".", "Main"),
                        List.of("PATH=/usr/bin:/bin"));
                case "python" -> new LangProfile(false,
                        List.of(), "main.py", null,
                        List.of("/usr/bin/python3", "main.py"),
                        List.of("PATH=/usr/bin:/bin"));
                default -> throw new IllegalArgumentException("Unsupported language: " + lang);
            };
        }
    }

    private record CompileOutcome(boolean failed,
                                  String fileId,
                                  String message,
                                  Long timeMillis,
                                  Long memoryKb,
                                  String stderr,
                                  Integer exitStatus,
                                  Long runTimeMillis) {
        static CompileOutcome success(String fileId, Long timeMillis, Long memoryKb, String stderr,
                                      Integer exitStatus, Long runTimeMillis) {
            return new CompileOutcome(false, fileId, "Accepted", timeMillis, memoryKb, stderr,
                    exitStatus, runTimeMillis);
        }

        static CompileOutcome failed(String message, Long timeMillis, Long memoryKb, String stderr,
                                     Integer exitStatus, Long runTimeMillis) {
            return new CompileOutcome(true, null, message, timeMillis, memoryKb, stderr,
                    exitStatus, runTimeMillis);
        }
    }

    private record CaseRunOutcome(SubmissionStatus terminalStatus,
                                  String message,
                                  Long timeMillis,
                                  Long memoryKb,
                                  String stdout,
                                  String stderr,
                                  Integer exitStatus,
                                  Long runTimeMillis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SandboxRunResult(String status,
                                    Integer exitStatus,
                                    Long time,
                                    Long memory,
                                    Long runTime,
                                    Integer procPeak,
                                    Map<String, String> files,
                                    String error,
                                    List<SandboxFileError> fileError,
                                    Map<String, String> fileIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SandboxFileError(String name, String type, String message) {
    }
}
