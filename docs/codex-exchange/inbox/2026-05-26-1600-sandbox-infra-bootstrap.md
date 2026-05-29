# Task: 沙箱基础设施引导 + 跨主机部署架构文档（Phase 1 of 5）
## Status: done
## Created by: Claude @ 2026-05-26T16:00+08:00
## Linked: outbox/2026-05-26-1600-sandbox-infra-bootstrap.md

---

## Prompt

ROLE
You are Codex executing **Phase 1 of 5** for the sandbox feature
designed by Claude. Read the entire file. When done, write the report
to `docs/codex-exchange/outbox/2026-05-26-1600-sandbox-infra-bootstrap.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

WHY PHASE 1
跑通 go-judge **沙箱容器本身**，并把跨主机部署架构写清楚，给后面 4
个 phase 提供：（a）真实可调用的 sandbox HTTP endpoint；（b）权威的
跨主机部署架构图，让 Inbox 2 (testcase zip 跨机分发) 和 Inbox 3
(SandboxClient 真实实现) 有 reference。

**本 phase 不动任何 Java / TS 源码。** 只动 `deploy/` 配置 + `docs/`。

DECISIONS ALREADY MADE BY USER (do not re-debate)
1. Sandbox engine = **go-judge** (criyle/go-judge)
2. 一期**不**采集 stack KB / syscall trace（只采 time/memory/stdout/stderr/exitCode/signal）
3. Testcase 文件传递 = **bind mount via shared docker volume**（worker
   和 sandbox 必须同机；worker 多副本时每台判题机都有一份本地 cache）
4. `SubmissionStatus` 会扩展加 MEMORY_LIMIT_EXCEEDED + OUTPUT_LIMIT_EXCEEDED
   （**Phase 2/3 才做**，本 phase 不动）
5. 部署形态：**后端服务器和判题机可以分离**。判题机通过 MQ + MySQL
   JDBC + HTTP testcase blob endpoint 与后端通信

CONSTRAINTS (hard)
- 只动这些文件：
  - `deploy/compose.yml`（新增 sandbox 服务、调整 judge-worker volumes、
    可能新增 profile `sandbox`）
  - 新建 `deploy/sandbox/`（go-judge 配置、自定义 image Dockerfile、
    启动脚本等，如需要）
  - `docs/deployment.md`（架构图 + 启动顺序 + 跨主机部署细节大改）
  - `docs/HANDOVER.md`（§3 进行中状态更新、§6 关键文件锚点加 go-judge）
  - `docs/codex-exchange/inbox/2026-05-26-1600-...` (Status only)
  - `docs/codex-exchange/outbox/2026-05-26-1600-...` (new)
- **不动**任何 .java / .ts / .vue / .sql / packages/** / apps/**
- 不引入 npm / maven 新依赖
- 不 commit / stage / push

============================================================
TASK 1 — go-judge 服务接入 compose
============================================================

在 `deploy/compose.yml` 新增 `sandbox` 服务，profile = `judge`（与
judge-worker 同 profile，确保它们在同一台判题机上一起启动）：

```yaml
sandbox:
  profiles: ["judge"]
  image: criyle/executorserver:latest        # go-judge 官方镜像（如 tag 不
                                              # 稳定，固定到 specific sha）
  privileged: true                            # cgroup / namespace / seccomp
                                              # 需要的权限；go-judge 文档明示
  environment:
    ES_HTTP_ADDR: ":8090"                     # go-judge HTTP listen
    ES_AUTH_TOKEN: ${SANDBOX_TOKEN:-}         # 鉴权 token
    ES_TIME_LIMIT: "60s"                      # 全局硬上限
    ES_OUTPUT_LIMIT: "256m"
  volumes:
    - testcase-cache:/aioj/cases:ro           # 只读 mount，worker 写
    - sandbox-tmp:/tmp                         # go-judge 临时工作目录
  networks:
    - aioj-judge
  healthcheck:
    test: ["CMD", "wget", "-q", "--spider", "http://localhost:8090/version"]
    interval: 10s
    timeout: 3s
    retries: 5
```

并修改现有 `judge-worker` 服务（compose.yml line ~202）：
- 加入同样的 `volumes: [testcase-cache:/aioj/cases]`（worker 端**可写**，
  go 解压 zip 后写到这里）
- 加入 `networks: [aioj-judge]`
- `SANDBOX_ENDPOINT` 默认改为 `http://sandbox:8090/execute`（已经是了）
- `depends_on` 增加 `sandbox: { condition: service_healthy }`

文件末尾 `volumes:` 段添加：
```yaml
volumes:
  testcase-cache:
  sandbox-tmp:
```

文件末尾 `networks:` 段添加（如已存在则归并）：
```yaml
networks:
  aioj-judge:
    driver: bridge
```

⚠️ 注意：`criyle/executorserver:latest` 的实际 image name 和 env var
**必须验证**。访问 `https://github.com/criyle/go-judge` 确认官方提供的
Docker image 名称、暴露端口、env vars。如果发现 image name 不同或不
存在官方镜像，**改用**自建 Dockerfile（基于 ubuntu + 编译 go-judge
binary 或下载 release）。在 outbox 报告里说明实际选择。

============================================================
TASK 2 — Multi-language runtime（如必要）
============================================================

go-judge 本身**只提供沙箱执行能力**，不内置编译器/解释器。需要让
sandbox 容器内能 exec：
- `javac` + `java` (JDK 17，与项目其他模块一致)
- `g++` (C++17)
- `python3` (3.10+)

两种做法：

**方案 A（推荐如可行）**：用 go-judge 官方 image 直接外加 apt install
- 写一个自定义 Dockerfile `deploy/sandbox/Dockerfile`：
  ```dockerfile
  FROM criyle/executorserver:latest    # 替换为实际官方 base
  USER root
  RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        g++ \
        python3 \
      && rm -rf /var/lib/apt/lists/*
  ```
- compose 改 `build: deploy/sandbox/` 替代 `image: ...`

**方案 B（如方案 A 不通）**：自建独立 sandbox image，参考 Judge0 的
language image 方案

调研 go-judge 文档（README.md / executorserver docs）选定方案并落
盘。如果 go-judge 设计本就要求语言运行时在 sandbox host 上预装而非
container 内（不太可能但要确认），在 outbox 里说明并提替代方案。

============================================================
TASK 3 — Cross-host deployment architecture（核心文档）
============================================================

完全改写 `docs/deployment.md` 中的"部署架构 / 启动顺序"部分（如不
存在该段则新建），包含：

### 3.1 部署形态

支持**三种形态**，文档要明示：

- **All-in-one**（dev / 小型生产）：所有 services 跑在同一台 host，
  `docker compose --profile infra --profile app --profile judge up`
- **Backend + Judge 分离**（推荐生产）：
  - **后端主机**：`infra` + `app` profiles。包含 MySQL / Redis /
    RabbitMQ / Nacos / Sentinel / gateway / auth / problem / ai /
    web-*。testcase zip 持久化到该主机的 volume
  - **判题主机（可多台）**：`judge` profile。包含 judge-worker +
    sandbox。通过环境变量指向后端主机的 MQ / DB 地址
- **K8s**（未来）：暂不实现，但文档预留扩展位

### 3.2 网络拓扑图

用 ASCII 画两种形态的拓扑（all-in-one 简化版 + 分离版完整版）。
分离版必须显示：

```
                Internet
                   │
       ┌───────────▼───────────┐
       │   Backend host (A)    │
       │  - gateway :8101 ←─ Internet
       │  - app services (内网)
       │  - MySQL/Redis/MQ/Nacos
       │  - testcase zip volume
       └───┬─────────────────┬─┘
           │ MQ              │ HTTP /api/v1/internal/...
           │ MySQL JDBC      │ (Phase 2 will add this endpoint)
           ▼                 ▼
   ┌───────────────┐  ┌───────────────┐
   │ Judge host B  │  │ Judge host C  │  ... ×N
   │  - worker     │  │  - worker     │
   │  - sandbox    │  │  - sandbox    │
   │  - shared vol │  │  - shared vol │
   │   (本机 only)  │  │   (本机 only)  │
   └───────────────┘  └───────────────┘
```

### 3.3 跨主机启动顺序

```
1. 后端主机：
   docker compose --profile infra up -d           # 启 MySQL/Redis/MQ/...
   docker compose --profile infra exec mysql ...  # 检查 Flyway migration 完成
   docker compose --profile app up -d              # 启业务服务

2. 判题主机（每台分别）：
   # 设置 .env：
   #   RABBITMQ_HOST=<后端主机内网 IP 或 DNS>
   #   MYSQL_URL=jdbc:mysql://<后端主机>:3306/ai_oj_next?...
   #   SANDBOX_TOKEN=<与后端 secrets 一致>
   #   PROBLEM_SERVICE_BASE_URL=http://<后端主机>:8101  # Phase 2 用
   docker compose --profile judge up -d           # 启 worker + sandbox
```

### 3.4 安全约束

- 后端主机 RabbitMQ 5672 端口和 MySQL 3306 端口**只在判题主机所在
  内网可达**，公网零暴露
- judge-worker 与 sandbox 之间只通过 `aioj-judge` docker network
- sandbox container 必须 `privileged: true`（cgroup 要求），但判题
  主机本身应做主机层加固（不跑其他业务、定期重建、内核版本受控）
- `SANDBOX_TOKEN` 通过 docker secrets 注入（参考现有
  `deploy/secrets/` 模板）

### 3.5 已知限制

- testcase zip 跨主机分发当前未实现 —— Phase 2 (Inbox 2) 会加
  HTTP blob endpoint。本 phase 完成后系统还**不能跑通真实判题**，
  只能验证 go-judge 容器本身可达
- Phase 3+ 才会实现 SandboxClient HTTP 调用

============================================================
TASK 4 — Verify go-judge actually runs (smoke test)
============================================================

跑一次本地 spike，验证 go-judge 起得来：

```bash
docker compose --profile judge up -d sandbox
sleep 5
curl http://localhost:8090/version
```

把 curl 实际响应内容贴到 outbox 报告（包括 HTTP status、JSON body、
go-judge 版本号）。

如果是新 image build（TASK 2 方案 A），还要测一次 multi-language
exec：用 curl 直接打 go-judge 的 `/run` 或 `/api/v1/run` endpoint
（具体 path 看 go-judge 文档）发个简单 Java/Python/C++ hello world，
把响应 JSON **全文**贴到 outbox。

这一步是关键 —— 给 Phase 2/3 提供 go-judge 真实响应字段（不是猜的）。
**`JudgeResult` DTO 设计**和 **OutputComparator 逻辑** 都会基于
这次实测的字段。

如果 go-judge 启动失败 / 路径不对 / 响应字段超出预期，停下来在
outbox 写 `## Status: blocked` 并说明现象 + 你的假设，让 Claude
决定怎么走。

============================================================
TASK 5 — Update HANDOVER
============================================================

在 `docs/HANDOVER.md`：
- §3 "进行中" 把"真实隔离沙箱"从🟠 调到 🟡 进行中，加 sub-bullet：
  "Phase 1 done: go-judge service in compose, multi-host topology
  documented"
- §6 关键文件锚点表加一行：
  | 沙箱服务 | `deploy/sandbox/Dockerfile` + `deploy/compose.yml#sandbox` |
- §5 已知风险加一条：
  "判题主机必须 `privileged: true` 跑 sandbox container，不应与其他
  业务混部，主机层加固按 docs/deployment.md §3.4"

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED (新增 + 修改):
     M  deploy/compose.yml
     M  docs/deployment.md
     M  docs/HANDOVER.md
     M  docs/codex-exchange/inbox/2026-05-26-1600-sandbox-infra-bootstrap.md
     ?? deploy/sandbox/         (新目录，至少含 Dockerfile)
     ?? docs/codex-exchange/outbox/2026-05-26-1600-sandbox-infra-bootstrap.md
   既有 baseline dirty files 可保留 — 列出但不回滚。
   **绝对不应该出现**任何 .java / .ts / .vue / .sql / packages/** /
   apps/** / backend/**.java 的修改。如出现立即 revert。

2. Focused diffs for each changed file (~15 lines context). For
   deploy/compose.yml, print the whole new `sandbox` block + the
   modified `judge-worker` block.

3. Section "Smoke test report"：
   - 实际用的 image / tag / sha
   - `curl /version` 响应全文
   - 三语言 hello world 的响应 JSON 全文（Java / C++ / Python）
   - go-judge 启动到 ready 的时间
   - 任何 anomaly / warning

4. Section "Architecture diagram"：把 docs/deployment.md §3.2 的
   ASCII 图复制到报告里供 Claude 验收

5. 5-sentence Chinese summary 覆盖：
   - 实际选用的 go-judge image / 自建 Dockerfile
   - 多语言运行时怎么装的
   - 三语言 smoke test 是否都通过
   - 跨主机部署文档关键约束（network / secrets / 启动顺序）
   - Phase 2 需要 Claude 关注的发现（go-judge 响应字段实际是什么）

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1600-sandbox-infra-bootstrap.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push, or modify any Java / TS / Vue / SQL /
i18n / app source.

## Constraints / Files in scope

**Touch (write)**:
- `deploy/compose.yml` (add sandbox service + modify judge-worker)
- `deploy/sandbox/` (new directory: Dockerfile, possibly entrypoint script)
- `docs/deployment.md` (architecture + cross-host deployment)
- `docs/HANDOVER.md` (§3 §5 §6)
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `https://github.com/criyle/go-judge` README（实际 image name + env vars + API path）
- `https://docs.docker.com/compose/compose-file/`（profile / volume / network 语法）
- `deploy/compose.yml` 现状全文
- `deploy/secrets/` 现有结构
- `docs/deployment.md` 现状

**Hard 禁区**:
- backend/**.java
- packages/**
- apps/**
- backend/api-contract/src/main/resources/db/migration/V*.sql
- 新增 maven / npm 依赖
- commit / stage / push
- 与 go-judge 无关的任何 deploy 改动（如改其他服务的 env / port）
