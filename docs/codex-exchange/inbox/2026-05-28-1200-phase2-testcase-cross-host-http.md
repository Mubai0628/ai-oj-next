# Task: Phase 2 — Testcase zip 跨主机 HTTP 分发
## Status: done
## Created by: Claude @ 2026-05-28T12:00+08:00
## Linked: outbox/2026-05-28-1200-phase2-testcase-cross-host-http.md

---

## Prompt

ROLE
You are Codex executing **Phase 2 of 5** for the sandbox feature.
Read this entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-28-1200-phase2-testcase-cross-host-http.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

WHY PHASE 2 NOW
Phase 1 + 1900 smoke test 确认 go-judge 沙箱本身可用。Phase 3 已完成
DTO/枚举扩展。**当前阻塞跨机部署的是 testcase zip 文件**——problem-service
把 zip 存到自己本机 `${user.home}/.ai-oj-next/testcases/`，判题机的
worker 跨机时**读不到**这些文件。

Phase 2 解决方案：problem-service 暴露一个 **internal HTTP blob
endpoint**，worker 跨机时按需 HTTP 拉取 + 本地 cache + sha256 校验。

DECISIONS LOCKED
- Endpoint: `GET /api/v1/internal/testcase-packages/{packageId}/blob`
- 鉴权: 用新环境变量 `AIOJ_INTERNAL_API_TOKEN`（与 SANDBOX_TOKEN 解耦——
  后者是 worker→sandbox，前者是 worker→problem-service）
- 流式返回（不把 zip 全加载到内存）
- worker 拉之前先读 testcase_packages 表拿 sha256（已经能跨机访问 MySQL）
- 本地 cache 命中策略：本地文件 sha256 与 DB sha256 一致即用 cache
- gateway **不**转发 `/api/v1/internal/**`，只对判题机内网开放
- 不动 V*.sql；testcase_packages 表已有 sha256 / file_size_bytes 字段够用

CONSTRAINTS (hard)
- 改动**仅限**：
  - `backend/common-lib/`（如需新增 internal token filter）
  - `backend/problem-service/`（新增 controller endpoint）
  - `backend/judge-worker/`（改造 TestcasePackageCache + 加 HTTP client）
  - `backend/api-contract/`（**不动**——不需要新 DTO，blob 是二进制流）
  - `deploy/compose.yml`（新增 env vars）
  - `docs/deployment.md`（说明跨主机 HTTP 链路 + 鉴权）
  - `docs/HANDOVER.md`（§3 状态、§4 待办勾掉）
- 不改 V*.sql，不改 SubmissionEntity，不动前端
- 不引入新 maven 依赖（Spring Boot 自带 `RestClient` / `WebClient` 已够；
  优先 RestClient——同步且简单）
- 不 commit / stage / push

============================================================
TASK 1 — 调研现状（read-only，写到 outbox Section 1）
============================================================

**先读、再写**。在动手前明确 read 以下文件并在 outbox 报告：

- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java`
  当前完整逻辑（重点：怎么找到 zip 文件路径、sha256 校验、解压目标）
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/`
  下所有 testcase 相关 entity/mapper（确认 worker 端已能读
  `testcase_packages` 表，拿哪些字段）
- `backend/problem-service/src/main/java/com/aioj/next/problem/domain/testcase/`
  下：TestcasePackageService、TestcaseStorageService（看 storage_path 怎么
  生成、文件物理位置）
- `backend/problem-service/src/main/java/com/aioj/next/problem/controller/TestcasePackageController.java`
  （现有的 /api/v1/problems/{id}/testcase-packages/* 控制器）
- `backend/api-contract/src/main/resources/db/migration/V4__testcase_package_storage.sql`
  （testcase_packages 表 schema，确认 sha256 / file_size_bytes / storage_path 字段）
- `backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java`
  （现有配置项）
- `backend/common-lib/src/main/java/com/aioj/next/common/security/`
  下所有现有 security filter（看是否已有 token-based filter 可复用）

输出到 outbox **Section 1: "调研现状"**：
- 现在 worker 怎么找 zip（API call sequence）
- testcase_packages 表 schema（关键字段）
- common-lib 是否已有 token filter 可复用

============================================================
TASK 2 — Add internal token filter to common-lib
============================================================

在 `backend/common-lib/src/main/java/com/aioj/next/common/security/`
新建 `InternalApiTokenFilter.java`：

```java
package com.aioj.next.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

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
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(HEADER);
        if (!StringUtils.hasText(expectedToken) || !expectedToken.equals(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "internal token missing or invalid");
            return;
        }
        // 注入 anonymous-internal authentication 让 @PreAuthorize 不阻塞
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
```

**重要**：不要直接在 SecurityFilterChain 注册——本 phase 不动 common-lib
全局 security config。让 problem-service 自己在 SecurityConfig 里**有选
择地**加这个 filter。

如果 common-lib 已经有 TokenFilter 类似的模式可复用，**优先复用**，在
outbox 说明。

============================================================
TASK 3 — problem-service 新增 internal blob endpoint
============================================================

新建 `backend/problem-service/src/main/java/com/aioj/next/problem/controller/InternalTestcaseController.java`：

```java
@RestController
@RequestMapping("/api/v1/internal/testcase-packages")
public class InternalTestcaseController {

    private final TestcasePackageService packageService;
    private final TestcaseStorageService storageService;  // 看现有类名调整

    public InternalTestcaseController(TestcasePackageService packageService,
                                      TestcaseStorageService storageService) {
        this.packageService = packageService;
        this.storageService = storageService;
    }

    /**
     * 流式下载指定 testcase package 的 zip blob。
     * 调用方：judge-worker（跨机判题主机）。
     * 鉴权：InternalApiTokenFilter 已校验 X-Internal-Token。
     */
    @GetMapping("/{packageId}/blob")
    public ResponseEntity<InputStreamResource> downloadBlob(@PathVariable Long packageId) {
        // 1. 查 package，确认 status = READY（不是 PROCESSING / FAILED）
        TestcasePackageEntity pkg = packageService.findReadyOrThrow(packageId);

        // 2. 拿物理 zip 路径
        Path zipPath = storageService.resolveZipPath(pkg);
        if (!Files.exists(zipPath)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "package zip not found on disk");
        }

        // 3. 流式返回，附 sha256 / file size header 供 worker 校验
        InputStreamResource body;
        try {
            body = new InputStreamResource(Files.newInputStream(zipPath));
        } catch (IOException ex) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "open package zip failed: " + ex.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(pkg.getFileSizeBytes())
                .header("X-Testcase-Sha256", pkg.getSha256())
                .header("X-Testcase-FileName", pkg.getFileName())
                .body(body);
    }
}
```

⚠️ 适配点：
- `packageService.findReadyOrThrow(packageId)` 可能现在没有这个方法，需
  要按现有 `TestcasePackageService` API 决定怎么调（可能用
  `findById(id)` + 手动校验 status；或新加 `findReadyOrThrow` 方法）
- `storageService.resolveZipPath(pkg)` 同理——根据现有 `TestcaseStorageService`
  / `LocalTestcaseStorageService` 的实际 API 名字调整。报告里说明实际
  调用
- header `X-Testcase-Sha256` / `X-Testcase-FileName` 让 worker 拉完后能
  校验完整性（不依赖 worker 跨机查 testcase_packages 表，但 worker 也
  可以查 DB 双重校验）

**Security 配置**：在 problem-service 的 SecurityConfig 注册
`InternalApiTokenFilter`（位置：在 BearerTokenAuthenticationFilter 之
**前**），并允许 `/api/v1/internal/**` 走这个 filter 不需要 user token。

============================================================
TASK 4 — problem-service config: AIOJ_INTERNAL_API_TOKEN
============================================================

在 `backend/problem-service/src/main/resources/application.yml`
加：

```yaml
aioj:
  ...
  internal:
    api-token: ${AIOJ_INTERNAL_API_TOKEN:}
```

并新建 `backend/problem-service/src/main/java/com/aioj/next/problem/config/InternalApiProperties.java`：

```java
@ConfigurationProperties(prefix = "aioj.internal")
public class InternalApiProperties {
    private String apiToken = "";
    // getter / setter
}
```

注册到主类 `@EnableConfigurationProperties`（如还没注册其他 properties
类一并加）。

如果 `apiToken` 为空 → InternalApiTokenFilter 拒绝所有 `/api/v1/internal/*`
请求（避免误开放）。

============================================================
TASK 5 — judge-worker config: PROBLEM_SERVICE_BASE_URL + INTERNAL_API_TOKEN
============================================================

`backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java`
加 2 个字段：

```java
private String problemServiceBaseUrl = "http://problem-service:8202";
private String internalApiToken = "";

// getter / setter
```

`backend/judge-worker/src/main/resources/application.yml` 同步：

```yaml
aioj:
  judge:
    ...
    problem-service-base-url: ${PROBLEM_SERVICE_BASE_URL:http://problem-service:8202}
    internal-api-token: ${AIOJ_INTERNAL_API_TOKEN:}
```

============================================================
TASK 6 — TestcasePackageCache HTTP-aware 改造
============================================================

⚠️ **这是 testcase 子域改动**（CLAUDE.md §8 重力井）。**最小改动原则**：
不要重写 TestcasePackageCache 整个类，只在"找到 zip 文件"的关键路径
插入 HTTP fallback。

现有逻辑（来自 1900 outbox 调研）：
- worker 查 testcase_packages 表，拿 storage_path / sha256 / file_size_bytes
- 当前 worker 直接读 storage_path 的本地文件

新逻辑：
1. worker 仍然查表拿 metadata（sha256 / file_size_bytes / 等）
2. 决定**本地 cache 路径**：`${cacheRoot}/blobs/${packageId}.zip`（**不
   再用** storage_path，因为跨机时它指向 problem-service 主机的路径）
3. 检查本地 cache：
   - 存在 + sha256 匹配 → 直接用
   - 不存在 OR sha256 不匹配 → HTTP 拉取 → 校验 → 写入
4. 后续解压、缓存逻辑不变

新加一个 `TestcaseBlobClient.java`（同 domain 包下）封装 HTTP 调用：

```java
@Component
public class TestcaseBlobClient {

    private static final Logger log = LoggerFactory.getLogger(TestcaseBlobClient.class);
    private final JudgeWorkerProperties properties;
    private final RestClient restClient;

    public TestcaseBlobClient(JudgeWorkerProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getProblemServiceBaseUrl())
                .defaultHeader("X-Internal-Token", properties.getInternalApiToken())
                .build();
    }

    /**
     * 流式下载 packageId 对应 zip，写入 destPath。
     * 返回服务端通过 header 提供的 sha256，供调用方校验。
     */
    public BlobHeaders downloadTo(Long packageId, Path destPath) {
        return restClient.get()
                .uri("/api/v1/internal/testcase-packages/{id}/blob", packageId)
                .exchange((req, res) -> {
                    if (res.getStatusCode().isError()) {
                        throw new DomainException(ErrorCode.INTERNAL_ERROR,
                                "Failed to fetch testcase blob: HTTP " + res.getStatusCode());
                    }
                    String sha256 = Optional.ofNullable(res.getHeaders().getFirst("X-Testcase-Sha256")).orElse("");
                    String fileName = Optional.ofNullable(res.getHeaders().getFirst("X-Testcase-FileName")).orElse("");
                    Files.createDirectories(destPath.getParent());
                    try (InputStream in = res.getBody()) {
                        Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return new BlobHeaders(sha256, fileName);
                });
    }

    public record BlobHeaders(String sha256, String fileName) {}
}
```

在 `TestcasePackageCache.java` 改造点（具体修改的方法/行，按现有代码
判断）：
- 找到"读取本地 zip 文件"的位置（可能是 `prepare()` 或 `ensureCached()`
  之类方法）
- 在读文件**之前**插入：
  - 算本地 cache 路径
  - 如果不存在或 sha256 不匹配 → 调 `TestcaseBlobClient.downloadTo(...)`
  - 拉完用 DB 的 sha256 校验新文件
- 其余解压、案例索引等逻辑保留不动

⚠️ 如果改动复杂度超过 50 行 / 影响多个方法，**停下来**在 outbox 标
blocked 并贴出 TestcasePackageCache 当前结构 + 你的改造提议，让 Claude
review 后再继续。**testcase 子域不允许激进重写**。

============================================================
TASK 7 — deploy/compose.yml 同步新环境变量
============================================================

在 `deploy/compose.yml`：

- `problem-service`（profile=app）的 environment 段加：
  ```yaml
  AIOJ_INTERNAL_API_TOKEN: ${AIOJ_INTERNAL_API_TOKEN:-}
  ```

- `judge-worker`（profile=judge）的 environment 段加：
  ```yaml
  AIOJ_INTERNAL_API_TOKEN: ${AIOJ_INTERNAL_API_TOKEN:-}
  PROBLEM_SERVICE_BASE_URL: ${PROBLEM_SERVICE_BASE_URL:-http://problem-service:8202}
  ```

不动其他服务。

============================================================
TASK 8 — Update docs/deployment.md
============================================================

在 `docs/deployment.md` 之前 Phase 1 加的 §3.5 "已知限制" 把
"testcase zip 跨主机分发当前未实现 —— Phase 2 will add this endpoint"
那条**删掉**或改为"✓ Phase 2 完成"。

新增一节（§3.6 或合适位置）"Internal Testcase Blob 链路"：

- Endpoint: `GET /api/v1/internal/testcase-packages/{packageId}/blob`
- 鉴权: `X-Internal-Token: ${AIOJ_INTERNAL_API_TOKEN}` header
- 网络: 仅判题主机内网可达；gateway **不**转发 `/api/v1/internal/**`
- 部署时必须在所有需要这条链路的服务（problem-service + judge-worker）
  同步设置同一个 token；不一致会 401
- worker 用 `PROBLEM_SERVICE_BASE_URL` 环境变量指向后端主机 8202 端口

更新跨主机启动顺序示例，加 `AIOJ_INTERNAL_API_TOKEN`。

============================================================
TASK 9 — Update HANDOVER.md
============================================================

- §3 进行中：把 Phase 2 状态标 done
- §4 待办：勾掉"testcase zip 跨机分发"
- §6 关键文件锚点：加新文件
  - `backend/problem-service/.../controller/InternalTestcaseController.java`
  - `backend/judge-worker/.../domain/TestcaseBlobClient.java`
  - `backend/common-lib/.../security/InternalApiTokenFilter.java`

============================================================
TASK 10 — compile 验收
============================================================

```bash
mvn -f backend/pom.xml -pl common-lib -am compile -q
mvn -f backend/pom.xml -pl problem-service -am compile -q
mvn -f backend/pom.xml -pl judge-worker -am compile -q
```

三个必须 exit 0。

注：本 Phase 不要求跑 integration test（需要 problem-service + worker
都起来）。手动验证留到 Phase 5 联调。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED 改动集合：
     M  backend/common-lib/...     (InternalApiTokenFilter 如新增)
     ?? backend/common-lib/src/main/java/com/aioj/next/common/security/InternalApiTokenFilter.java
     M  backend/problem-service/src/main/resources/application.yml
     M  backend/problem-service/src/main/java/com/aioj/next/problem/.../SecurityConfig.java   (添加 filter)
     ?? backend/problem-service/src/main/java/com/aioj/next/problem/controller/InternalTestcaseController.java
     ?? backend/problem-service/src/main/java/com/aioj/next/problem/config/InternalApiProperties.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java
     M  backend/judge-worker/src/main/resources/application.yml
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java
     ?? backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcaseBlobClient.java
     M  deploy/compose.yml
     M  docs/deployment.md
     M  docs/HANDOVER.md
     M  docs/codex-exchange/inbox/2026-05-28-1200-phase2-testcase-cross-host-http.md
     ?? docs/codex-exchange/outbox/2026-05-28-1200-phase2-testcase-cross-host-http.md
   基线 baseline dirty files 保留。**不应**出现 V*.sql / 前端 / api-contract 改动。

2. Focused diffs for each modified file. New files print full content.

3. Section "调研现状" 的报告（TASK 1 输出）

4. Section "TestcasePackageCache 改造摘要"：
   - 改了哪个方法 / 哪几行
   - HTTP fallback 触发条件
   - sha256 校验前后
   - 改动行数 < 50（否则改 blocked）

5. compile 三个 exit 0

6. Manual verification checklist（人工后续做）：
   ```
   [ ] 启 problem-service + 设 AIOJ_INTERNAL_API_TOKEN
   [ ] curl -H "X-Internal-Token: <token>" http://localhost:8202/api/v1/internal/testcase-packages/<id>/blob -o /tmp/x.zip
   [ ] 检查 X-Testcase-Sha256 header + 下载文件 sha256 一致
   [ ] 错误 token → 401
   [ ] missing token → 401
   [ ] non-READY package → 404 or 400
   [ ] worker 端配 PROBLEM_SERVICE_BASE_URL + INTERNAL_API_TOKEN，触发一次判题任务，验证从 HTTP 拉取了 zip
   ```

7. 5-sentence Chinese summary：
   - 调研发现 + TestcasePackageCache 改造范围
   - 新 endpoint / filter / blob client 三件套位置
   - 鉴权方式（与 SANDBOX_TOKEN 解耦的理由）
   - common-lib filter 是新建还是复用现有
   - Phase 4 已 unblock 哪些（SandboxClient 真实实现可以开始）

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-28-1200-phase2-testcase-cross-host-http.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `backend/common-lib/src/main/java/com/aioj/next/common/security/InternalApiTokenFilter.java` (new)
- `backend/problem-service/src/main/java/com/aioj/next/problem/controller/InternalTestcaseController.java` (new)
- `backend/problem-service/src/main/java/com/aioj/next/problem/config/InternalApiProperties.java` (new)
- `backend/problem-service/src/main/java/com/aioj/next/problem/config/SecurityConfig.java` (modify: register filter)
- `backend/problem-service/src/main/resources/application.yml` (add aioj.internal.api-token)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java` (add 2 fields)
- `backend/judge-worker/src/main/resources/application.yml` (add 2 env mappings)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcaseBlobClient.java` (new)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java` (minimal HTTP fallback insertion)
- `deploy/compose.yml`
- `docs/deployment.md`
- `docs/HANDOVER.md`
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `backend/api-contract/src/main/resources/db/migration/V4__testcase_package_storage.sql`
- 现有 TestcasePackage 子域代码（TASK 1 列出）

**Hard 禁区**:
- 任何 `V*.sql` migration（DB 不动）
- `SubmissionEntity` / `TestcasePackageEntity` 表字段
- api-contract（DTO 不动）
- 前端任何文件
- 新增 maven / npm 依赖
- 重写 TestcasePackageCache（只允许最小插入，>50 行改动 → blocked）
- commit / stage / push
