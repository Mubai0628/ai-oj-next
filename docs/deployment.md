# Deployment

## 1. Local Compose

```powershell
cd D:\studyProject\ai-oj-next
copy .env.example .env
docker compose -f deploy/compose.yml --profile infra up -d
docker compose -f deploy/compose.yml --profile app --profile judge up -d --build
```

## 2. Ports

- Gateway: `8101`
- Auth service: `8201`
- Problem service: `8202`
- Judge worker: `8203`
- Sandbox (go-judge, local host binding): `8090`
- AI service: `8204`
- Nacos: `8848`, `9848`, `9849`, `7848`, console `8080`
- Sentinel dashboard: `8858`
- RabbitMQ management: `15672`
- Prometheus: `9090`
- Grafana: `3000`

## 3. Deployment Architecture

### 3.1 部署形态

支持三种部署形态：

- **All-in-one（dev / 小型生产）**：所有服务跑在同一台 host。
  ```powershell
  docker compose -f deploy/compose.yml --profile infra --profile app --profile judge up -d --build
  ```
- **Backend + Judge 分离（推荐生产）**：
  - **后端主机 A**：`infra` + `app` profiles。包含 MySQL / Redis / RabbitMQ / Nacos / Sentinel / gateway / auth / problem / ai / web-*。测试包原始 zip 持久化在后端主机 volume。
  - **判题主机 B/C/...**：`judge` profile。每台包含 `judge-worker` + `sandbox`，通过环境变量连接后端主机上的 MQ、MySQL、Nacos 和 internal testcase blob HTTP endpoint。
  - **判题机本地共享卷**：`judge-worker` 写 `testcase-cache`，`sandbox` 以只读方式挂载同一卷；该共享卷只在单台判题机内共享，不跨主机。
- **K8s（未来）**：暂不实现；后续需要把 `judge-worker + sandbox` 做成同节点部署单元，并用 node affinity 保证共享卷语义。

### 3.2 网络拓扑图

All-in-one：

```text
┌──────────────────────────────────────────────────────────────┐
│ Single host                                                  │
│  ┌──────────────┐       RabbitMQ       ┌──────────────────┐  │
│  │ problem-svc  │ ───────────────────▶ │ judge-worker     │  │
│  └──────────────┘                      │  writes cache    │  │
│        │                               └────────┬─────────┘  │
│        │ MySQL / Redis / Nacos / Sentinel       │ /aioj/cases │
│        ▼                                        ▼            │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ sandbox (go-judge) on aioj-judge network, :8090        │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

Backend + Judge 分离：

```text
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
           │ MySQL JDBC      │ X-Internal-Token
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

后端主机：

```powershell
docker compose -f deploy/compose.yml --profile infra up -d
# 等 MySQL / RabbitMQ / Nacos / Redis healthcheck 通过，并确认 Flyway migration 完成
docker compose -f deploy/compose.yml --profile app up -d --build
```

判题主机（每台分别配置 `.env` 后启动）：

```env
RABBITMQ_HOST=<后端主机内网 IP 或 DNS>
MYSQL_URL=jdbc:mysql://<后端主机>:3306/ai_oj_next?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SANDBOX_TOKEN=<与后端 secrets 一致>
AIOJ_INTERNAL_API_TOKEN=<problem-service 与 judge-worker 共享的内部 token>
PROBLEM_SERVICE_BASE_URL=http://<后端主机>:8202
SANDBOX_ENDPOINT=http://sandbox:8090/execute
```

```powershell
docker compose -f deploy/compose.yml --profile judge up -d --build
```

### 3.3.1 Sandbox runtime image

`deploy/sandbox/Dockerfile` 以 `criyle/go-judge:v1.12.0` 提供 sandbox 引擎，
并用 `eclipse-temurin:17-jdk-jammy` 作为运行时基础层，安装 `python3` 与
`g++`。JDK 复制到 `/usr/lib/jvm/temurin-17`，并提供 `/usr/bin/java` /
`/usr/bin/javac`，这样 go-judge 的隔离执行环境挂载 `/usr` 时可以稳定找到
Java 编译与运行命令。

2026-05-29 已在 Windows + Docker Desktop 上构建 `aioj-sandbox-runtime:e2e`
并验证：

- `GET /version` 返回 go-judge `v1.12.0`
- Python hello world → `Accepted`
- C++ `g++` 编译 + 运行 → `Accepted`
- Java `javac Main.java` + `java -cp . Main` → `Accepted`

### 3.4 安全约束

- 后端主机 RabbitMQ `5672` 和 MySQL `3306` 端口只允许判题主机所在内网访问，禁止公网暴露。
- `judge-worker` 与 `sandbox` 只通过 `aioj-judge` Docker network 通信，`sandbox` 的 `8090` 端口仅绑定 `127.0.0.1` 供本机 smoke test 和排障。
- `sandbox` container 需要 `privileged: true` 和 `shm_size: 256m`，因为 go-judge 在容器内创建嵌套受限执行环境并依赖 cgroup / namespace 能力。
- 判题主机不应与其他业务混部；建议独立主机、受控内核版本、最小开放端口、定期重建镜像与主机补丁。
- `SANDBOX_TOKEN` 通过 Docker secrets 或 host-mounted secret file 注入；开发环境可为空，生产必须设置非空值。
- `AIOJ_INTERNAL_API_TOKEN` 只用于 `judge-worker` 拉取 `problem-service` 内部测试包 blob，和 `SANDBOX_TOKEN` 分离，避免 worker→sandbox 与 worker→backend 两条链路共用凭据。

### 3.5 已知限制

- ✓ Phase 2 已实现 testcase zip 跨主机 HTTP blob 分发；判题主机按需拉取后端主机上的测试包并校验 sha256。
- ✓ Phase 4 已实现 `SandboxClient` 对 go-judge `/run` 的真实 HTTP 调用、逐 case 输出比较、状态映射和 runtime 字段落库。
- ✓ Phase 5 已交付包含 Python / C++ / Java runtime 的 sandbox image，并完成提交→评测→详情展示端到端联调。
- 尚未覆盖大测试点压测：50MB / 100MB zip、断点重试、非法路径、hash 校验、worker 缓存、缺包/坏包审计链路仍需专项验证。

### 3.6 Internal Testcase Blob 链路

- Endpoint: `GET /api/v1/internal/testcase-packages/{packageId}/blob`
- 鉴权: 请求头 `X-Internal-Token: ${AIOJ_INTERNAL_API_TOKEN}`；problem-service 与 judge-worker 必须配置同一个 token，不一致会返回 401。
- 网络: 只允许判题主机内网访问 problem-service `8202`；gateway 不转发 `/api/v1/internal/**`。
- 数据: problem-service 流式返回原始 zip，并通过 `X-Testcase-Sha256` / `X-Testcase-FileName` header 暴露元数据；worker 下载到本地 cache 后按 DB sha256 再校验。
- 配置: judge-worker 通过 `PROBLEM_SERVICE_BASE_URL` 指向后端主机 problem-service，例如 `http://<后端主机>:8202`。
- 生产部署: `AIOJ_INTERNAL_API_TOKEN` 建议使用 Docker secrets 或 host-mounted secret file 注入；不要和 `SANDBOX_TOKEN` 共用。

### 3.7 本地 IDEA 联调注意

- IDEA 直接启动后端服务、Docker 只跑 sandbox 时，`judge-worker` 必须直连宿主机 problem-service：`PROBLEM_SERVICE_BASE_URL=http://127.0.0.1:8202`。
- 本地默认内部 token 为 `dev-internal-token`；如覆盖 `AIOJ_INTERNAL_API_TOKEN`，problem-service 与 judge-worker 必须完全一致。
- Docker Compose judge profile 仍通过环境变量覆盖为 `http://problem-service:8202`，不要把容器网络服务名用于 IDEA 宿主机进程。
- 若提交显示 `Testcase package unavailable: failed to fetch testcase package blob`，先验证内部 blob 链路：
  ```powershell
  curl.exe -i -H "X-Internal-Token: dev-internal-token" `
    http://127.0.0.1:8202/api/v1/internal/testcase-packages/<packageId>/blob `
    -o $env:TEMP\aioj-blob-test.zip
  ```
  该命令必须返回 `200`，再排查 judge-worker 或 sandbox。

## 4. Production Notes

- Keep Nacos, MySQL, Redis, RabbitMQ, and Sentinel on an internal network.
- Use Docker secrets or host-mounted secret files for JWT private keys, AI keys,
  database passwords, and sandbox tokens.
- Scale stateless services with additional replicas. Scale judge workers
  independently according to RabbitMQ queue depth.
- Enable Flyway in exactly one deployment wave before increasing replicas, or
  let the first app wave run migrations while the old system remains online.

## Flyway 迁移失败的恢复 SOP

### 触发条件
启动报：`Detected failed migration to version N (...). Please remove
any half-completed changes then run repair to fix the schema history.`

### 根因
Flyway 跑每条 migration 的流程是「插占位行 → 执行 SQL → 成功改
success=1 / 失败改 success=0」。MySQL 的 DDL 不在事务里，一条 V*.sql
里有多个 DDL 时前面成功的不会回滚，会留下"半完成 schema + Flyway 记
failed"的状态。下次启动 Flyway.validate() 见 success=0 直接拒绝继续。

常见触发场景：
- 之前手工在 DB 试过同样的 ALTER/CREATE INDEX，导致 V*.sql 跑时遇
  Duplicate column / Duplicate key name
- 多实例同时启动同时尝试 migrate
- DDL 超时（表正被长事务占用 / 网络抖断）

### 处置流程（按顺序，不要跳步）

#### Step 1 止血
- 关掉所有未启动成功的实例
- 多实例部署时，确保**只有 1 个实例**带 `FLYWAY_ENABLED=true`，其余
  `FLYWAY_ENABLED=false`，等首个实例迁移完成后再启

#### Step 2 诊断（只读）
```sql
SELECT version, description, script, checksum, installed_on,
       execution_time, success
FROM flyway_schema_history
WHERE success = 0;

-- 对照失败的 V{N}.sql 内容，逐张表确认 schema 是否已变更
DESC <表名>;
SHOW INDEX FROM <表名>;
```

#### Step 3 分情况修复

| DB 实际状态 vs V{N}.sql 预期 | 处置 |
|---|---|
| 完全没生效 | `DELETE FROM flyway_schema_history WHERE version='N' AND success=0;` 重启即可重跑 |
| 部分生效 | 手动跑剩余 DDL；之后 `UPDATE flyway_schema_history SET success=1, execution_time=0 WHERE version='N';` |
| 完全生效（仅 Flyway 没记成功） | `UPDATE flyway_schema_history SET success=1 WHERE version='N';` |

#### Step 4 重启服务
确认 `SELECT * FROM flyway_schema_history WHERE success=0;` 为空。

#### Step 5 复盘
- 是否有人手动改过 DB？ → 团队提醒：所有 schema 变更走 V{N+1}.sql
- 是否多实例并发？ → 部署脚本必须序列化 migrate
- 是否 DDL 超时？ → 大表变更挪到业务低峰窗口

### 预防（建议补到 CLAUDE.md §7 禁忌速查）

| 不要 | 替代 |
|---|---|
| 直接在生产 DB 客户端 ALTER 表 | 走 V{N+1}.sql |
| 多个服务实例同时 migrate | 部署时只让 1 个实例带 FLYWAY_ENABLED=true |
| 修改已跑过的 V{N}.sql | 永远新建 V{N+1}.sql（V{N} checksum 校验会立即报错） |
