# Task: 修复 sandbox build 网络阻塞 + 绕过 compose 校验 + 重跑 smoke test
## Status: blocked
## Created by: Claude @ 2026-05-26T18:00+08:00
## Linked: outbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md

---

## Prompt

ROLE
You are Codex executing a focused fix-and-retest task designed by
Claude. Read the entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

CONTEXT — 来自上一轮 smoke test (outbox 2026-05-26-1700) 的两个发现

**Issue A**：容器内 Debian apt 源 `deb.debian.org` 在国内多次 502
Gateway Error（IP 151.101.2.132），导致 `apt-get update && apt-get
install` 失败，sandbox image build 不出来。这跟 Docker Desktop 的
registry-mirrors 配置无关（registry-mirrors 影响 docker pull 而非
容器内 apt），需要在 Dockerfile 里换 apt 源。

**Issue B**：只启 `--profile judge` 时 compose 校验 `judge-worker
depends on undefined service rabbitmq` 失败。`--profile infra
--profile judge` 能绕过但会拉起整套 infra（MySQL/Redis/MQ/Nacos），
重而无意义。本任务**不修 compose.yml**（留给 Phase 2/3 一并处理
应用层依赖重试），改为**绕开 compose**直接用 `docker build` + `docker
run` 验证 sandbox。

GOAL
1. 修 `deploy/sandbox/Dockerfile` apt 源到清华 TUNA
2. 用 `docker build` 直接 build sandbox image（不走 compose）
3. 用 `docker run` 直接启 sandbox container（不走 compose）
4. 重跑上轮 inbox (2026-05-26-1700) 的 13 个 Section smoke test，拿
   go-judge 真实响应字段

CONSTRAINTS (hard)
- **可以**修改 `deploy/sandbox/Dockerfile`（只这一个文件）
- 不动 `deploy/compose.yml` 或其他任何源代码 / 文档
- 不引入新 npm / maven 依赖
- 不 commit / stage / push

============================================================
TASK 1 — Patch Dockerfile to use TUNA apt mirror
============================================================

编辑 `deploy/sandbox/Dockerfile`。当前的 stage-1 是：

```dockerfile
FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ca-certificates \
      g++ \
      openjdk-17-jdk-headless \
      python3 \
      wget \
    && rm -rf /var/lib/apt/lists/*
```

改为（在 `apt-get update` 之前先换源）：

```dockerfile
FROM debian:bookworm-slim

# 用清华 TUNA Debian 镜像替换默认 deb.debian.org，
# 避免国内 502 / 拉取慢。先用 HTTP（避免 ca-certificates 鸡生蛋
# 问题），等 ca-certificates 装上后下游可自行升级 HTTPS。
# Debian 12 (bookworm) 默认使用 deb822 格式：/etc/apt/sources.list.d/debian.sources
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources; \
        sed -i 's|http://security.debian.org|http://mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources; \
    fi; \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list; \
        sed -i 's|http://security.debian.org|http://mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list; \
    fi

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ca-certificates \
      g++ \
      openjdk-17-jdk-headless \
      python3 \
      wget \
    && rm -rf /var/lib/apt/lists/*
```

**保留其余部分不动**（multi-stage COPY go-judge binary、EXPOSE、
ENTRYPOINT）。

如果 TUNA 也连不上（部分网络环境会），fallback 选项（按顺序试）：
- 阿里云：`http://mirrors.aliyun.com`
- 中科大：`http://mirrors.ustc.edu.cn`
- 网易：`http://mirrors.163.com`

只在第一个 mirror build 失败后才换。outbox 报告里说明实际生效的
mirror。

============================================================
TASK 2 — Build sandbox image WITHOUT compose
============================================================

绕开 compose project 校验（Issue B），直接用 docker build：

```powershell
cd D:\studyProject\ai-oj-next
docker build -t aioj-sandbox:smoke -f deploy/sandbox/Dockerfile deploy/sandbox
```

记录：
- 总耗时（用 `Measure-Command { ... }`）
- 镜像大小：`docker images aioj-sandbox:smoke`
- 是否还有 WARNING / ERROR
- 如果 TUNA 也 502，换 fallback mirror（任务 1 列表）再试一次

输出到 outbox **Section 2: "Build sandbox (direct docker build)"**。

============================================================
TASK 3 — Run sandbox WITHOUT compose
============================================================

直接 `docker run` 启 sandbox，复刻 compose.yml 里 sandbox 服务的
关键配置（privileged / port / env / shm_size）：

```powershell
docker run -d `
  --name aioj-sandbox-smoke `
  --privileged `
  --shm-size 256m `
  -p 127.0.0.1:8090:8090 `
  -e ES_HTTP_ADDR=":8090" `
  -e ES_OUTPUT_LIMIT="268435456" `
  -e ES_COPY_OUT_LIMIT="67108864" `
  -e ES_FILE_TIMEOUT="30m" `
  aioj-sandbox:smoke
```

不 mount 任何 volume（本测试不需要 testcase-cache）。

等 15-30 秒后：

```powershell
docker ps --filter name=aioj-sandbox-smoke
docker logs --tail=80 aioj-sandbox-smoke
curl.exe -i http://localhost:8090/version
```

输出到 outbox **Section 3: "Run sandbox & /version response"**：
- ps 输出
- logs 末尾 80 行
- curl /version 完整响应（headers + body）

============================================================
TASKS 4-11 — 三语言 hello world + 异常场景
============================================================

按上一轮 inbox 2026-05-26-1700 的 TASK 6-11（已经写好的请求体
模板）依次跑：

- TASK 4 = 原 TASK 6: Python hello world
- TASK 5 = 原 TASK 7: C++ compile + run（**用 `fileIds` 引用而非
  字面占位符**，详见下方）
- TASK 6 = 原 TASK 8: Java compile + run（同上）
- TASK 7 = 原 TASK 9: TLE
- TASK 8 = 原 TASK 10: Runtime Error
- TASK 9 = 原 TASK 11: Output Limit

⚠️ **C++ / Java 编译 + 运行的正确写法**（来自上轮 Codex 调研，
更正了 Claude 草稿的占位符错误）：

第一个 `/run` 请求：编译 + `copyOutCached: ["main"]`
响应包含 `fileIds: {"main": "<file-id>"}`

第二个 `/run` 请求：执行 + `copyIn: {"main": {"fileId": "<上一步的 file-id>"}}`

Codex 必须**先发第一个请求**，**解析响应里的 fileId**，**再构造第
二个请求**。可以分两次 curl，中间用 PowerShell 解析 JSON 拿 fileId。

测完每个 fileId 之后**必须** `DELETE /file/<fileId>` 释放，否则
go-judge 内存会泄漏：

```powershell
curl.exe -X DELETE http://localhost:8090/file/<fileId>
```

每个 Section 必须**完整贴**：
- 请求 body（多步的两个都要）
- 响应 body（多步的两个都要）
- 字段解读

============================================================
TASK 10 — Field mapping table（关键 Phase 2/3 输入）
============================================================

基于 TASK 3-9 实测响应，输出一个**字段映射表**到 outbox
**Section 10: "go-judge → AIOJ field mapping (from real test data)"**：

| go-judge 字段 | 实测值示例 | 类型 / 单位 | AIOJ JudgeResult 字段 | 转换规则 |
|---|---|---|---|---|
| `status` | "Accepted" | string | `status: SubmissionStatus` | map |
| `time` | 5234567 | ns | `timeMillis: long` | `/ 1_000_000` |
| `memory` | 8388608 | byte | `memoryKb: long` | `/ 1024` |
| `runTime` | 12345678 | ns (wall) | optional | `/ 1_000_000` |
| `exitStatus` | 0 | int | `exitCode: int` | direct |
| `files.stdout` | "hello world\n" | string | `stdout: string` | direct |
| ... | ... | ... | ... | ... |

**最重要**：列出实测出现的所有 `status` 字符串值（"Accepted" /
"Time Limit Exceeded" / "Nonzero Exit Status" / "Signalled" / ...），
这是 Phase 3 `SubmissionStatus` mapping 的输入。

============================================================
TASK 11 — Cleanup
============================================================

```powershell
docker stop aioj-sandbox-smoke
docker rm aioj-sandbox-smoke
```

不删 image（让用户后续可以复用）。

输出到 outbox **Section 11: "Cleanup"** 记录命令 + 状态。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  deploy/sandbox/Dockerfile
     M  docs/codex-exchange/inbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md
     ?? docs/codex-exchange/outbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md
   既有 baseline dirty files 保留但**不应**新增其他文件。

2. Outbox 11 个 Section 全部输出，每个 Section 的响应/日志都是实测
   原文（不是从文档抄的描述）。

3. 5-sentence Chinese summary：
   - 实际生效的 apt mirror（TUNA / 阿里 / 中科大 / 网易）
   - sandbox image build 总耗时
   - 三语言 hello world 是否都通过 + 编译产物 fileId 模式是否如
     文档所述工作
   - TLE / RE / OLE 实测响应的 `status` 字符串值
   - 字段映射表里最值得 Phase 2/3 关注的 1-2 个发现

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push, or modify any source code outside
`deploy/sandbox/Dockerfile`.

## Constraints / Files in scope

**Touch (write)**:
- `deploy/sandbox/Dockerfile` (apt mirror sed only)
- This inbox (Status only)
- The outbox (new, 11 sections)

**Read for reference**:
- `outbox/2026-05-26-1700-sandbox-smoke-test.md`（Section 5 已抓到的
  go-judge schema 直接复用）
- `outbox/2026-05-26-1600-sandbox-infra-bootstrap.md`（compose 配置
  参考，**只读**）
- `https://github.com/criyle/go-judge` README（如需复查 schema）

**Hard 禁区**:
- `deploy/compose.yml`（不动，留给 Phase 2/3）
- backend/** / packages/** / apps/**
- 任何 .java / .ts / .vue / .sql
- commit / stage / push
