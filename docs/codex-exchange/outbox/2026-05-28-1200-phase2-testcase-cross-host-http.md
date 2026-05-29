# Task: Phase 2 — Testcase zip 跨主机 HTTP 分发
## Status: done
## Created by: Codex @ 2026-05-28T12:00+08:00
## Linked: inbox/2026-05-28-1200-phase2-testcase-cross-host-http.md

---

## Report

### 1. 调研现状

- `TestcasePackageCache.prepareActivePackage(problemId)` 当前先查 worker 侧可访问的 `testcase_packages` 表，筛选 `problem_id + active=true + status=READY`，再要求 `storage_provider=LOCAL`。
- 旧逻辑用 `JudgeTestcaseProperties.storageRoot + testcasePackage.storageKey` 拼本机 zip 路径，调用 `verifyZipFile()` 校验文件存在、大小、sha256，然后解压到 `${cacheRoot}/{packageId}-{sha8}`。
- worker 端已有 `TestcasePackageEntity` / `TestcasePackageCaseEntity` 以及 mapper，能从 MySQL 读取 `id/problemId/fileName/fileSizeBytes/sha256/status/active/storageProvider/storageKey/caseCount/sampleCount` 和用例 input/output path。
- V4 schema 中 `testcase_packages` 已有关键字段：`file_size_bytes BIGINT NOT NULL`、`sha256 CHAR(64) NOT NULL`、`storage_provider VARCHAR(32) NOT NULL`、`storage_key VARCHAR(512) NOT NULL`、`status VARCHAR(32)`、`active TINYINT`；无需 DB 迁移。
- problem-service 当前 `LocalTestcaseStorageService` 把 zip 存到 `packages/{problemId}/{sha256}.zip`，`TestcasePackageService.saveReadyPackage()` 写 `storage_provider=LOCAL` 和 `storage_key=merged.storageKey()`。
- common-lib 只有 `BearerTokenAuthenticationFilter`，没有现成 token-based internal filter 可复用；因此本次新增 `InternalApiTokenFilter`。

### 2. git status --short

本任务新增/修改：

```text
?? backend/common-lib/src/main/java/com/aioj/next/common/security/InternalApiTokenFilter.java
?? backend/problem-service/src/main/java/com/aioj/next/problem/config/InternalApiProperties.java
?? backend/problem-service/src/main/java/com/aioj/next/problem/controller/InternalTestcaseController.java
?? backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcaseBlobClient.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/ProblemServiceApplication.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/config/SecurityConfig.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/domain/testcase/TestcasePackageService.java
 M backend/problem-service/src/main/resources/application.yml
 M backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java
 M backend/judge-worker/src/main/resources/application.yml
 M deploy/compose.yml
 M docs/deployment.md
 M docs/HANDOVER.md
 M docs/codex-exchange/inbox/2026-05-28-1200-phase2-testcase-cross-host-http.md
?? docs/codex-exchange/outbox/2026-05-28-1200-phase2-testcase-cross-host-http.md
```

既有 baseline dirty files 保留，包括 Phase 3 DTO/enum 改动、web/admin 前序 exchange 改动、sandbox compose/Dockerfile 等；本任务没有修改 `V*.sql`、前端、api-contract、`SubmissionEntity`、`JudgeAuditLogEntity` 或新增依赖。

### 3. New files

#### `backend/common-lib/src/main/java/com/aioj/next/common/security/InternalApiTokenFilter.java`

```java
package com.aioj.next.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class InternalApiTokenFilter extends OncePerRequestFilter {
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
    private static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalApiTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER);
        if (!StringUtils.hasText(expectedToken) || !expectedToken.equals(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "internal token missing or invalid");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
```

#### `backend/problem-service/src/main/java/com/aioj/next/problem/config/InternalApiProperties.java`

```java
package com.aioj.next.problem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aioj.internal")
public class InternalApiProperties {
    private String apiToken = "";

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
```

#### `backend/problem-service/src/main/java/com/aioj/next/problem/controller/InternalTestcaseController.java`

```java
package com.aioj.next.problem.controller;

import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.problem.domain.testcase.TestcasePackageService;
import com.aioj.next.problem.domain.testcase.TestcaseStorageService;
import com.aioj.next.problem.persistence.entity.TestcasePackageEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/internal/testcase-packages")
public class InternalTestcaseController {
    private final TestcasePackageService testcasePackageService;
    private final TestcaseStorageService storageService;

    public InternalTestcaseController(TestcasePackageService testcasePackageService,
                                      TestcaseStorageService storageService) {
        this.testcasePackageService = testcasePackageService;
        this.storageService = storageService;
    }

    @GetMapping("/{packageId}/blob")
    public ResponseEntity<InputStreamResource> downloadBlob(@PathVariable Long packageId) {
        TestcasePackageEntity testcasePackage = testcasePackageService.findReadyOrThrow(packageId);
        Path zipPath = storageService.resolveStorageKey(testcasePackage.getStorageKey());
        if (!Files.isRegularFile(zipPath)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "package zip not found on disk");
        }

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(testcasePackage.getFileSizeBytes())
                    .header("X-Testcase-Sha256", testcasePackage.getSha256())
                    .header("X-Testcase-FileName", testcasePackage.getFileName())
                    .body(new InputStreamResource(Files.newInputStream(zipPath)));
        } catch (IOException ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "open package zip failed: " + ex.getMessage());
        }
    }
}
```

#### `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcaseBlobClient.java`

```java
package com.aioj.next.judge.domain;

import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.judge.config.JudgeWorkerProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
public class TestcaseBlobClient {
    private final RestClient restClient;

    public TestcaseBlobClient(JudgeWorkerProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getProblemServiceBaseUrl())
                .defaultHeader("X-Internal-Token", properties.getInternalApiToken())
                .build();
    }

    public BlobHeaders downloadTo(Long packageId, Path destPath) {
        return restClient.get()
                .uri("/api/v1/internal/testcase-packages/{id}/blob", packageId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new DomainException(ErrorCode.INTERNAL_ERROR,
                                "Failed to fetch testcase blob: HTTP " + response.getStatusCode());
                    }

                    String sha256 = Optional.ofNullable(response.getHeaders().getFirst("X-Testcase-Sha256"))
                            .orElse("");
                    String fileName = Optional.ofNullable(response.getHeaders().getFirst("X-Testcase-FileName"))
                            .orElse("");
                    try {
                        Files.createDirectories(destPath.getParent());
                        try (InputStream input = response.getBody()) {
                            Files.copy(input, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException ex) {
                        throw new DomainException(ErrorCode.INTERNAL_ERROR,
                                "Failed to write testcase blob: " + ex.getMessage());
                    }
                    return new BlobHeaders(sha256, fileName);
                });
    }

    public record BlobHeaders(String sha256, String fileName) {
    }
}
```

### 4. Focused diffs

#### Security / endpoint

```diff
 @EnableConfigurationProperties({JwtProperties.class, TestcaseProperties.class, InternalApiProperties.class})
```

```diff
 .authorizeHttpRequests(auth -> auth
+        .requestMatchers("/api/v1/internal/**").hasRole("INTERNAL")
         .requestMatchers("/problems/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
         .anyRequest().authenticated())
+ .addFilterBefore(new InternalApiTokenFilter(internalApiProperties.getApiToken()),
+         UsernamePasswordAuthenticationFilter.class)
  .addFilterBefore(new BearerTokenAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
```

```diff
+public TestcasePackageEntity findReadyOrThrow(Long packageId) {
+    TestcasePackageEntity testcasePackage = requirePackage(packageId);
+    if (testcasePackage.getStatus() != TestcasePackageStatus.READY) {
+        throw new DomainException(ErrorCode.BAD_REQUEST, "Only READY testcase packages can be downloaded");
+    }
+    return testcasePackage;
+}
```

```diff
 aioj:
+  internal:
+    api-token: ${AIOJ_INTERNAL_API_TOKEN:}
   testcase:
```

#### judge-worker config

```diff
 private String cacheRoot = System.getProperty("user.home") + "/.ai-oj-next/judge-cache";
+private String problemServiceBaseUrl = "http://problem-service:8202";
+private String internalApiToken = "";
```

```diff
     language-whitelist: ${JUDGE_LANGUAGE_WHITELIST:java,cpp,python}
     cache-root: ${AIOJ_JUDGE_CACHE_ROOT:${user.home}/.ai-oj-next/judge-cache}
+    problem-service-base-url: ${PROBLEM_SERVICE_BASE_URL:http://problem-service:8202}
+    internal-api-token: ${AIOJ_INTERNAL_API_TOKEN:}
```

#### TestcasePackageCache

```diff
     private final TestcasePackageMapper packageMapper;
     private final TestcasePackageCaseMapper caseMapper;
+    private final TestcaseBlobClient blobClient;
     private final Path cacheRoot;
@@
-        Path zipPath = resolveStorageKey(testcasePackage.getStorageKey());
-        verifyZipFile(testcasePackage, zipPath);
+        Path zipPath = ensureBlobCached(testcasePackage);
```

```diff
+private Path ensureBlobCached(TestcasePackageEntity testcasePackage) {
+    Path blobPath = blobPath(testcasePackage);
+    if (isMatchingZip(testcasePackage, blobPath)) {
+        return blobPath;
+    }
+    TestcaseBlobClient.BlobHeaders headers;
+    try {
+        headers = blobClient.downloadTo(testcasePackage.getId(), blobPath);
+    } catch (RuntimeException ex) {
+        throw new TestcasePackageUnavailableException("failed to fetch testcase package blob", ex);
+    }
+    if (headers.sha256() != null && !headers.sha256().isBlank()
+            && !headers.sha256().equalsIgnoreCase(testcasePackage.getSha256())) {
+        throw new TestcasePackageUnavailableException("downloaded testcase package SHA-256 header does not match metadata");
+    }
+    verifyZipFile(testcasePackage, blobPath);
+    return blobPath;
+}
```

#### deploy/docs

```diff
 problem-service:
   environment:
+    AIOJ_INTERNAL_API_TOKEN: ${AIOJ_INTERNAL_API_TOKEN:-}
 judge-worker:
   environment:
+    AIOJ_INTERNAL_API_TOKEN: ${AIOJ_INTERNAL_API_TOKEN:-}
+    PROBLEM_SERVICE_BASE_URL: ${PROBLEM_SERVICE_BASE_URL:-http://problem-service:8202}
```

```diff
-testcase zip 跨主机分发当前未实现；Phase 2 会新增 HTTP blob endpoint
+✓ Phase 2 已实现 testcase zip 跨主机 HTTP blob 分发
+### 3.6 Internal Testcase Blob 链路
+- Endpoint: GET /api/v1/internal/testcase-packages/{packageId}/blob
+- 鉴权: X-Internal-Token: ${AIOJ_INTERNAL_API_TOKEN}
+- 网络: gateway 不转发 /api/v1/internal/**
```

### 5. TestcasePackageCache 改造摘要

- 改动方法：`prepareActivePackage()` 从直接 `resolveStorageKey(storageKey)` 改为 `ensureBlobCached(testcasePackage)`。
- 新增方法：`ensureBlobCached()`、`isMatchingZip()`、`blobPath()`。
- HTTP fallback 触发条件：`${cacheRoot}/blobs/{packageId}.zip` 不存在，或本地文件大小 / sha256 与 DB metadata 不一致。
- sha256 校验：下载前用原 `verifyZipFile()` 判定 cache 命中；下载后先校验响应 header `X-Testcase-Sha256`（若存在），再用 DB sha256 和 `verifyZipFile()` 校验落盘文件。
- 后续解压路径、`.ready` marker、case path 校验和 `preparedCases()` 逻辑保持不变。
- diff: `36 insertions(+), 10 deletions(-)`，低于 inbox 的 50 行阈值，未重写 testcase 子域。

### 6. compile outputs

```text
$ mvn -f backend/pom.xml -pl common-lib -am compile -q
(no output; command exited 0)
```

```text
$ mvn -f backend/pom.xml -pl problem-service -am compile -q
(no output; command exited 0)
```

```text
$ mvn -f backend/pom.xml -pl judge-worker -am compile -q
(no output; command exited 0)
```

### 7. Manual verification checklist

```text
[ ] 启 problem-service + 设 AIOJ_INTERNAL_API_TOKEN
[ ] curl -H "X-Internal-Token: <token>" http://localhost:8202/api/v1/internal/testcase-packages/<id>/blob -o /tmp/x.zip
[ ] 检查 X-Testcase-Sha256 header + 下载文件 sha256 一致
[ ] 错误 token → 401
[ ] missing token → 401
[ ] non-READY package → 404 or 400
[ ] worker 端配 PROBLEM_SERVICE_BASE_URL + INTERNAL_API_TOKEN，触发一次判题任务，验证从 HTTP 拉取了 zip
```

### 8. Chinese summary

调研确认旧 worker 依赖 DB 的 `storage_key` 去读本机 `${storageRoot}`，跨主机时必然找不到 problem-service 主机上的 zip；本次只在 `prepareActivePackage()` 前置 zip 获取路径加了 HTTP-aware cache，解压和用例索引逻辑未重写。新增三件套分别是 common-lib 的 `InternalApiTokenFilter`、problem-service 的 `InternalTestcaseController`、judge-worker 的 `TestcaseBlobClient`。鉴权使用 `AIOJ_INTERNAL_API_TOKEN` 和 `X-Internal-Token`，与 `SANDBOX_TOKEN` 解耦，因为 worker→backend 下载测试包和 worker→sandbox 执行代码是两条不同信任边界。common-lib 没有可复用内部 token filter，所以本次新建了最小专用 filter，并让 token 为空时拒绝所有 internal 请求。Phase 4 现在可以继续做真实 `SandboxClient` 调用、逐 case 执行、状态映射和资源审计，不再被跨主机 testcase zip 文件不可达卡住。

## Next-action hint

- Phase 4 联调时建议先用 manual checklist 验证 blob endpoint，再触发真实判题任务观察 worker 是否命中 `${cacheRoot}/blobs/{packageId}.zip`。
