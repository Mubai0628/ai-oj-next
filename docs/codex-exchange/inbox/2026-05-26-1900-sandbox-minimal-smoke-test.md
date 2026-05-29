# Task: 用官方 go-judge image 跑最小 smoke test，解锁 Phase 2/3 DTO 设计
## Status: done
## Created by: Claude @ 2026-05-26T19:00+08:00
## Linked: outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md

---

## Prompt

ROLE
You are Codex executing a **fallback smoke test** designed by Claude.
Read the entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

CONTEXT — 上一轮 1800 outbox 暴露的硬阻塞
- TUNA / 阿里 / 中科大 / 网易 4 个 Debian apt mirror 全部 503 或
  100 kB/s 慢速 → multi-language sandbox image build 彻底走不通
- 累计已经烧了 25+ 分钟 build 时间，再换 mirror 也是同病
- 真因疑似 WSL2 → Windows → 国内 ISP 出口对 HTTP CDN 大文件连接不
  稳，不是某个 mirror 的问题

KEY INSIGHT —— **不需要 multi-language image 才能解锁 Phase 2/3**
go-judge `/run` 响应的 **JSON 结构**（status / time / memory /
exitStatus / files / fileIds / fileError 等）跟用户跑的是 Java / C++
/ Python / shell 无关 —— 只是 args 不同 → 命令不同 → 不同 status
字符串。Phase 2/3 DTO 设计需要的是**字段名 + 类型 + 单位 + status
枚举字符串值**，这些用 busybox/shell 就能完整覆盖。

DECISION: 这一轮**直接用官方 `criyle/go-judge:v1.12.0` 原版 image**
（不自定义 build、不装多语言 runtime），跑 shell 命令验证响应结构。
multi-language 的 apt 网络问题留作单独的离线/预构建问题，后续处理。

CONSTRAINTS (hard)
- **不修改任何文件**（包括 Dockerfile）。本轮纯 docker run + curl，
  read-only on repo
- 不动 deploy/compose.yml
- 不 commit / stage / push
- 每个 `docker pull` / `docker run` / `docker exec` / `curl` 必须设
  合理 timeout，**单步不超过 5 分钟**。超时立即 abort + 在 outbox
  标 partial 并继续下一个独立步骤

============================================================
TASK 1 — Pull official go-judge image
============================================================

```powershell
docker pull criyle/go-judge:v1.12.0
docker images criyle/go-judge:v1.12.0
```

如果之前的 build attempt 已经把 image 拉到 layer cache，pull 会很
快确认。记录耗时 + image 大小。

输出到 outbox **Section 1: "Pull official image"**。

如果 pull 超过 5 分钟没完成 → mark blocked + stop。

============================================================
TASK 2 — Inspect image contents (探测 image 里有哪些 binary 可用)
============================================================

go-judge 官方 image 是个 minimal sandbox runner，需要确认里面有哪
些工具能用作 `/run` 的 args 入参：

```powershell
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "ls -la /bin /usr/bin 2>/dev/null | head -100"
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "which sh echo cat ls true false sleep yes 2>/dev/null"
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "cat /etc/os-release 2>/dev/null"
```

如果 `--entrypoint sh` 失败（说明 image 没 sh），改用：

```powershell
docker run --rm criyle/go-judge:v1.12.0 --help
docker inspect criyle/go-judge:v1.12.0 --format='{{json .Config}}'
```

记录 image 是 distroless / alpine / debian 哪种基础，哪些工具能用。

输出到 outbox **Section 2: "Image contents inspection"**。

============================================================
TASK 3 — 启动 sandbox（官方 image，无自定义）
============================================================

```powershell
docker run -d `
  --name aioj-sandbox-minimal `
  --privileged `
  --shm-size 256m `
  -p 127.0.0.1:8090:8090 `
  -e ES_HTTP_ADDR=":8090" `
  criyle/go-judge:v1.12.0

Start-Sleep -Seconds 10
docker ps --filter name=aioj-sandbox-minimal
docker logs --tail=80 aioj-sandbox-minimal
```

输出到 outbox **Section 3: "Run official sandbox"**：
- ps 输出
- logs 末尾 80 行（看启动是否成功，监听 port 是否 8090）

如果 container exit / restart loop，看 logs 报错，标 blocked。

============================================================
TASK 4 — /version + /config 拿元信息
============================================================

```powershell
curl.exe -s -i http://localhost:8090/version
curl.exe -s -i http://localhost:8090/config
```

输出到 outbox **Section 4: "/version & /config"**：
- 两个响应的 HTTP status + headers + body **全文**

`/config` 通常返回 go-judge 内部 capabilities（如 cgroup version /
默认资源限制 / 是否支持 stream 等），对 Phase 2/3 设计很关键。

============================================================
TASK 5 — 最小 /run：echo hello
============================================================

基于 Section 2 探测到的可用 binary，构造最小 hello world。如果有
`/bin/echo` 或 `/bin/sh`：

```json
{
  "cmd": [
    {
      "args": ["/bin/echo", "hello-from-sandbox"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 10240},
        {"name": "stderr", "max": 10240}
      ],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50
    }
  ]
}
```

或 fallback 用 sh：

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "echo hello-from-sandbox"],
      ...
    }
  ]
}
```

PowerShell 发请求：

```powershell
$body = @'
{...上面那个 JSON...}
'@
$body | Out-File -Encoding utf8 -FilePath $env:TEMP/minimal-echo.json
curl.exe -s -X POST http://localhost:8090/run -H "Content-Type: application/json" --data-binary "@$env:TEMP/minimal-echo.json"
```

输出到 outbox **Section 5: "Minimal /run (echo)"**：
- 请求 body 全文
- 响应 body **全文**（不省略任何字段）
- 关键字段实测值（status / time / memory / runTime / exitStatus /
  files.stdout / 任何额外字段）

============================================================
TASK 6 — 异常场景: TLE
============================================================

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "while true; do :; done"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 100000000,
      "memoryLimit": 67108864,
      "procLimit": 50
    }
  ]
}
```

`cpuLimit: 100000000` = 100ms。期望响应 `status == "Time Limit Exceeded"`。

输出到 outbox **Section 6: "TLE behavior"**：
- 请求 + 响应 全文
- 实测 `status` 字符串
- 实测 `time` 值（应约等于 100_000_000 ns）

============================================================
TASK 7 — 异常场景: Runtime Error (exit non-zero)
============================================================

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "exit 7"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50
    }
  ]
}
```

输出到 outbox **Section 7: "Runtime Error (non-zero exit)"**：
- 实测 `status`（应该是 "Nonzero Exit Status" 或类似）
- 实测 `exitStatus` = 7

============================================================
TASK 8 — 异常场景: Signalled (segfault-like)
============================================================

如果 image 里有工具能造 SIGSEGV / SIGKILL，跑一个看 signal 行为。
最简单：

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "kill -SEGV $$"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50
    }
  ]
}
```

输出到 outbox **Section 8: "Signalled behavior"**：
- 实测 `status` 字符串（应该是 "Signalled" 或类似）
- 看响应有没有 `signal` 字段、字段名实际是什么

============================================================
TASK 9 — 异常场景: Output Limit Exceeded
============================================================

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "yes xxxxxxxxxxxxx 2>/dev/null"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 1024},
        {"name": "stderr", "max": 1024}
      ],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50
    }
  ]
}
```

`stdout max: 1024` 设小，`yes` 会快速触发。

输出到 outbox **Section 9: "Output Limit Exceeded"**：
- 实测 `status` 字符串（应该是 "Output Limit Exceeded"）
- `files.stdout` 是否被截断到 1024 bytes

============================================================
TASK 10 — Memory Limit Exceeded（如可行）
============================================================

go-judge 内部 sandbox 可能用 cgroup memory limit。试试用 sh 申请
大内存：

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "yes | head -c 200000000 > /tmp/big.txt"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 5000000000,
      "memoryLimit": 16777216,
      "procLimit": 50
    }
  ]
}
```

`memoryLimit: 16777216` = 16MB，试 200MB 分配。

输出到 outbox **Section 10: "MLE behavior (best effort)"**：
- 实测 `status`
- 如果 image 里没 sh / head / yes 这条无法跑，记录 skipped + 原因

============================================================
TASK 11 — Compile + Run with fileId（验证编译产物缓存机制）
============================================================

虽然没有 g++ / javac，但 go-judge image 应该有 sh，可以**模拟**
copyOut/copyIn 流程：

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "echo '#!/bin/sh' > runner; echo 'echo cached-ok' >> runner; chmod +x runner"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50,
      "copyOutCached": ["runner"]
    }
  ]
}
```

期望响应包含 `fileIds: {"runner": "<some-id>"}`。

记下 fileId，发第二个请求：

```json
{
  "cmd": [
    {
      "args": ["./runner"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [{"content": ""}, {"name": "stdout", "max": 1024}, {"name": "stderr", "max": 1024}],
      "cpuLimit": 1000000000,
      "memoryLimit": 67108864,
      "procLimit": 50,
      "copyIn": {
        "runner": {"fileId": "<上一步的 fileId>"}
      }
    }
  ]
}
```

期望 `files.stdout` == `"cached-ok\n"`。

输出到 outbox **Section 11: "fileId compile-cache simulation"**：
- 第一请求 + 响应 全文（**重点贴出 fileIds 字段格式**）
- fileId 值（举例：`"abc123def456"`）
- 第二请求 + 响应 全文

完成后清理：

```powershell
curl.exe -X DELETE http://localhost:8090/file/<fileId>
```

============================================================
TASK 12 — Field mapping table（关键 Phase 2/3 输入）
============================================================

基于 TASK 5-11 实测响应，输出**字段映射表**到 outbox
**Section 12: "go-judge → AIOJ field mapping (verified)"**：

| go-judge 字段 | 实测值示例 | 类型 / 单位 | 备注 |
|---|---|---|---|
| `status` | "Accepted" / "Time Limit Exceeded" / ... | string | **必列实测所有 status 字符串** |
| `time` | XXX | ns | cgroup recorded |
| `memory` | XXX | byte | |
| `runTime` | XXX | ns | wall clock |
| `exitStatus` | 0 / 7 / ... | int | |
| `files.stdout` | "..." | string | |
| `files.stderr` | "..." | string | |
| `fileIds.<name>` | "<id>" | string | 编译产物 cache 引用 |
| 其他实测出现的字段 | ... | ... | ... |

**关键交付物**：
1. 实测出现的 `status` 字符串集合（5+ 种）
2. signal 字段实际名字 / 是否存在
3. `procPeak` / `fileError` 是否在某些响应出现
4. fileId 字符串格式（长度、字符集）

============================================================
TASK 13 — Cleanup
============================================================

```powershell
docker stop aioj-sandbox-minimal
docker rm aioj-sandbox-minimal
```

不删 official image（用户后续可复用）。

输出到 outbox **Section 13: "Cleanup"**。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  docs/codex-exchange/inbox/2026-05-26-1900-sandbox-minimal-smoke-test.md
     ?? docs/codex-exchange/outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md
   **No source file modified**。

2. Outbox 13 个 Section 全部输出。每个 /run 响应必须是**实测原文**，
   不是从文档抄的描述。

3. 5-sentence Chinese summary：
   - 官方 image 启动是否成功
   - 实测 status 字符串集合（最少 3 种）
   - fileId cache 机制是否如文档所述工作
   - 哪些字段在响应里**实际存在**，哪些**没出现**
   - Phase 2/3 DTO 设计是否可以基于本轮数据展开

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push, or modify any project files.

## Constraints / Files in scope

**Touch (write)**:
- This inbox (Status only)
- The outbox (new, 13 sections)
- OS temp dir 临时 JSON 文件（自删）

**Read for reference**:
- `https://github.com/criyle/go-judge` README（schema 参考）
- `outbox/2026-05-26-1700-sandbox-smoke-test.md` Section 5（已抓的
  schema 描述）

**Hard 禁区**:
- `deploy/sandbox/Dockerfile`（本轮不动）
- `deploy/compose.yml`
- backend/** / packages/** / apps/**
- 任何 .java / .ts / .vue / .sql
- 不要再尝试 multi-language apt build（已经 4 次失败，明确放弃这条
  路径）
