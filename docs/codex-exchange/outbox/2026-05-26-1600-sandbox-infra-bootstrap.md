# Task: 沙箱基础设施引导 + 跨主机部署架构文档（Phase 1 of 5）
## Status: done
## Created by: Codex @ 2026-05-26T15:53:06+08:00
## Linked: inbox/2026-05-26-1600-sandbox-infra-bootstrap.md

---

## Report

### Section 1: git status --short

本任务新增 / 修改：

```text
 M deploy/compose.yml
 M docs/HANDOVER.md
 M docs/deployment.md
?? deploy/sandbox/Dockerfile
?? docs/codex-exchange/inbox/2026-05-26-1600-sandbox-infra-bootstrap.md
?? docs/codex-exchange/outbox/2026-05-26-1600-sandbox-infra-bootstrap.md
```

当前工作区还保留了前序 exchange 任务的 baseline dirty files，未回滚、未改动：

```text
 M CLAUDE.md
 M apps/web-admin/package.json
 M apps/web-admin/src/components/ProblemEditorDrawer.vue
 M apps/web-admin/src/components/TestcasePackageUploader.vue
 M apps/web-admin/src/styles.css
 M apps/web-admin/src/views/AiDraftsView.vue
 M apps/web-admin/src/views/DashboardView.vue
 M apps/web-admin/src/views/ProblemsView.vue
 M apps/web-admin/src/views/RegisterView.vue
 M apps/web-admin/src/views/RolesView.vue
 M apps/web-admin/src/views/UsersView.vue
 M apps/web-user/src/components/problem/ProblemPane.vue
 M apps/web-user/src/components/problem/ProblemTabs.vue
 M apps/web-user/src/main.ts
 M apps/web-user/src/styles.css
 M apps/web-user/src/types/problem-workspace.ts
 M apps/web-user/src/views/ProblemDetailView.vue
 M apps/web-user/src/views/SubmissionsView.vue
 M package-lock.json
 M packages/i18n/src/messages.ts
?? apps/web-admin/src/components/TestcaseManifestEditor.vue
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? docs/codex-exchange/README.md
?? docs/codex-exchange/inbox/.gitkeep
?? docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/inbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
?? docs/codex-exchange/inbox/2026-05-26-1430-drawer-content-stretch-fix.md
?? docs/codex-exchange/inbox/2026-05-26-1530-sandbox-design-research.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
?? docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md
?? docs/codex-exchange/outbox/2026-05-26-1530-sandbox-design-research.md
```

本任务未修改任何 Java / TS / Vue / SQL / packages / apps 源码文件。

### Section 2: focused diffs

`deploy/compose.yml`：`judge-worker` 增加数据库连接、判题缓存卷、独立 judge network，并依赖 sandbox healthcheck。

```diff
   judge-worker:
     profiles: ["judge"]
     build:
       context: ..
       dockerfile: deploy/docker/backend.Dockerfile
       args:
         SERVICE_MODULE: judge-worker
     environment:
       SERVER_PORT: 8203
+      MYSQL_URL: ${MYSQL_URL:-jdbc:mysql://mysql:3306/${MYSQL_DATABASE:-ai_oj_next}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
+      MYSQL_USER: ${MYSQL_USER:-aioj}
+      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-change-me}
       RABBITMQ_HOST: rabbitmq
       RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER:-aioj}
       RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS:-change-rabbit-me}
       NACOS_ENABLED: ${NACOS_ENABLED:-true}
       NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR:-nacos:8848}
+      AIOJ_JUDGE_CACHE_ROOT: /aioj/cases
       SANDBOX_ENDPOINT: ${SANDBOX_ENDPOINT:-http://sandbox:8090/execute}
       SANDBOX_TOKEN: ${SANDBOX_TOKEN:-}
+    volumes:
+      - testcase-cache:/aioj/cases
+    networks:
+      - default
+      - aioj-judge
     depends_on:
       rabbitmq:
         condition: service_healthy
       nacos:
         condition: service_healthy
+      sandbox:
+        condition: service_healthy
```

`deploy/compose.yml`：新增 sandbox 服务、共享 volumes 和 `aioj-judge` network。

```yaml
  sandbox:
    profiles: ["judge"]
    build:
      context: sandbox
    privileged: true
    shm_size: 256m
    environment:
      ES_HTTP_ADDR: ":8090"
      ES_AUTH_TOKEN: ${SANDBOX_TOKEN:-}
      ES_OUTPUT_LIMIT: "268435456"
      ES_COPY_OUT_LIMIT: "67108864"
      ES_FILE_TIMEOUT: "30m"
    ports:
      - "127.0.0.1:8090:8090"
    volumes:
      - testcase-cache:/aioj/cases:ro
      - sandbox-tmp:/tmp
    networks:
      - aioj-judge
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:8090/version"]
      interval: 10s
      timeout: 3s
      retries: 5

volumes:
  testcase-cache:
  sandbox-tmp:

networks:
  aioj-judge:
    driver: bridge
```

`deploy/sandbox/Dockerfile`：

```dockerfile
FROM criyle/go-judge:v1.12.0 AS go-judge

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ca-certificates \
      g++ \
      openjdk-17-jdk-headless \
      python3 \
      wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY --from=go-judge /opt/go-judge /opt/mount.yaml /opt/

EXPOSE 8090/tcp 5051/tcp 5052/tcp

ENTRYPOINT ["./go-judge"]
```

`docs/deployment.md`：新增 §3 部署架构、跨主机启动顺序、安全约束和限制；同时在端口列表加入 sandbox `8090`。

```diff
-## Ports
+## 2. Ports
 ...
 - Judge worker: `8203`
+- Sandbox (go-judge, local host binding): `8090`
 - AI service: `8204`
 ...
-## Production Notes
+## 3. Deployment Architecture
+### 3.1 部署形态
+### 3.2 网络拓扑图
+### 3.3 跨主机启动顺序
+### 3.4 安全约束
+### 3.5 已知限制
+## 4. Production Notes
```

`docs/HANDOVER.md`：更新真实隔离沙箱状态、风险和关键文件锚点。

```diff
+| 真实隔离沙箱 | 🟡 进行中 | Phase 1 done: go-judge service in compose, multi-host topology documented |
 ...
+- **判题主机 privileged sandbox**：判题主机必须 `privileged: true` 跑
+  sandbox container，不应与其他业务混部，主机层加固按 docs/deployment.md
+  §3.4。
 ...
+| 沙箱服务 | `deploy/sandbox/Dockerfile` + `deploy/compose.yml#sandbox` |
```

`docs/codex-exchange/inbox/2026-05-26-1600-sandbox-infra-bootstrap.md`：顶部状态已从 `new` 改为 `done`。

```diff
-## Status: new
+## Status: done
```

### Section 3: Smoke test report

资料核对：
- 官方 Docker image 按 go-judge 文档为 `criyle/go-judge`，REST API 包含 `POST /run` 与 `GET /version`。
- Docker Hub 当前可见 tag 包含 `v1.12.0`；本次 Dockerfile 使用 `criyle/go-judge:v1.12.0` 作为 go-judge binary 来源，而不是任务草稿里的旧名 `criyle/executorserver:latest`。
- go-judge 官方安装文档给出的自建镜像模式是 multi-stage copy `/opt/go-judge` 和 `/opt/mount.yaml` 后安装语言运行时，本次采用该模式。

本机执行结果：

```text
> Get-Command docker -ErrorAction SilentlyContinue; docker compose -f deploy\compose.yml --profile judge config
docker : The term 'docker' is not recognized as the name of a cmdlet, function, script file, or operable program.
```

因此 smoke test 未能在当前 Codex 环境执行：

| 项目 | 结果 |
|---|---|
| 实际 image / tag / sha | `criyle/go-judge:v1.12.0`；sha/digest 未能拉取，因为本机无 Docker CLI |
| `curl /version` 响应全文 | 未执行；Docker CLI 不存在，sandbox 无法启动 |
| Java hello world `/run` | 未执行；Docker CLI 不存在 |
| C++ hello world `/run` | 未执行；Docker CLI 不存在 |
| Python hello world `/run` | 未执行；Docker CLI 不存在 |
| go-judge ready time | 未测量；sandbox 未启动 |
| anomaly / warning | 当前机器缺少 Docker CLI；后续应在有 Docker Engine 的 judge host 上补跑 `/version` 和三语言 `/run`，把真实响应字段反馈给 Phase 2/3 |

### Section 4: Architecture diagram

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

### Section 5: verification

可执行验证：

```text
docker compose config / build / up: 未执行成功，原因是当前环境没有 docker 命令。
```

只读范围检查：

```text
本任务改动集中在 deploy/compose.yml、deploy/sandbox/Dockerfile、docs/deployment.md、docs/HANDOVER.md、1600 inbox/outbox。
没有修改 Java / TS / Vue / SQL / packages / apps 源码。
```

人工后续 checklist：

- [ ] 在安装 Docker Engine 的机器上运行 `docker compose -f deploy/compose.yml --profile judge config`
- [ ] 运行 `docker compose -f deploy/compose.yml --profile judge up -d --build sandbox`
- [ ] 运行 `curl -i http://localhost:8090/version`
- [ ] 用 go-judge `POST /run` 分别补测 Java / C++ / Python hello world
- [ ] 把三语言真实响应字段交给 Phase 2/3，用于 DTO 与状态映射设计
- [ ] 若生产启用 `SANDBOX_TOKEN`，确认 `/version` healthcheck 是否需要鉴权 header

### Section 6: Chinese summary

本次实际选用 `criyle/go-judge:v1.12.0` 作为 go-judge 来源，并通过 `deploy/sandbox/Dockerfile` 自建包含 `g++`、`openjdk-17-jdk-headless`、`python3` 的 sandbox image。compose 里新增了 `sandbox` 服务、`aioj-judge` 网络、`testcase-cache`/`sandbox-tmp` volumes，并让 `judge-worker` 与 sandbox 同 profile、同判题机启动。三语言 smoke test 没有通过也没有失败，而是被当前环境缺少 Docker CLI 阻塞；这部分需要在真实 judge host 上补跑。跨主机部署文档已经写清后端主机与判题主机的 MQ/MySQL/Nacos/HTTP blob 边界、启动顺序、内网暴露约束和 `privileged` sandbox 风险。Phase 2 需要 Claude 重点关注：补测 go-judge `/run` 的真实响应字段，并把 `SANDBOX_ENDPOINT` 从当前兼容 stub 的 `/execute` 语义收敛到真实 go-judge `/run` 或适配层。

## Next-action hint

- 本地环境没有 Docker，Phase 2/3 前最好先在一台带 Docker Engine 的 judge host 上补一份真实 `/version` 和三语言 `/run` 响应样本。
- 如果 `SANDBOX_TOKEN` 在 go-judge 中会保护 `/version`，compose healthcheck 需要跟进改成带 header 或使用无需鉴权的健康路径。
