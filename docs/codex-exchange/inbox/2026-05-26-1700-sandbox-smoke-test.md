# Task: go-judge 沙箱实测验证（Phase 1 收尾，为 Phase 2/3 提供真实响应字段）
## Status: blocked
## Created by: Claude @ 2026-05-26T17:00+08:00
## Linked: outbox/2026-05-26-1700-sandbox-smoke-test.md

---

## Prompt

ROLE
You are Codex executing a **smoke test** task designed by Claude. Read
the entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-26-1700-sandbox-smoke-test.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

WHY THIS TASK
Phase 1 (inbox 2026-05-26-1600) 已经写好 compose + Dockerfile，但当
时没有 Docker 跑实测。用户**刚刚装好 Docker Desktop for Windows**，
现在补 smoke test，关键目的：

1. 验证 go-judge sandbox 容器在 Windows + Docker Desktop 能正常起来
2. **拿到 go-judge 真实响应字段** —— Phase 2/3 的 `JudgeResult` DTO
   扩展 / status 映射设计必须基于真实数据，不能猜
3. 验证三语言（Java / C++ / Python）都能在 sandbox 内 hello world
4. 验证异常场景（TLE / RE）的响应字段表现

PRINCIPLE: 「先证伪后处方」(lessons/debugging/visual-css-bugs.md 的
通用原则)。不要根据 go-judge 文档"猜"响应字段，要把**实测响应**贴
outbox，让 Claude 看到真实数据后再设计 Phase 2/3 DTO。

CONSTRAINTS (hard)
- **不动任何源代码**。只跑 docker / curl 命令，写 outbox 报告
- 不动 deploy/ / docs/ 任何文件（Phase 1 已经搭好骨架）
- 不 commit / stage / push
- 如果环境/网络问题导致测试不能完成，停下来在 outbox 写 `blocked`
  + 具体阻塞点

ENVIRONMENT NOTES
- 用户机器: Windows + Docker Desktop (WSL2 backend)
- 项目根: `D:\studyProject\ai-oj-next`
- **PowerShell 的 `curl` 是 `Invoke-WebRequest` 别名，行为不同于真
  curl**。本任务所有 curl 命令必须用 `curl.exe` 显式调用真实 curl
  binary（Windows 10+ 自带），或用 `Invoke-RestMethod` 的等价写法
- 国内拉镜像可能慢 —— 如果用户已在 Docker Desktop 配过镜像加速，
  build 应该 OK；如果某一步超过 15 分钟无进展，记录到 outbox 标
  blocked

============================================================
TASK 1 — Docker 环境验证
============================================================

跑：

```powershell
docker --version
docker compose version
docker info | Select-String -Pattern "OSType|Operating System|Server Version|Kernel Version"
```

把输出贴 outbox **Section 1: "Docker environment"**。

如果任一命令报 "not recognized" / "Cannot connect to the Docker
daemon"，标 blocked 并停。否则继续。

============================================================
TASK 2 — Build sandbox image
============================================================

```powershell
cd D:\studyProject\ai-oj-next
docker compose -f deploy/compose.yml --profile judge build sandbox
```

记录：
- 总耗时（可用 `Measure-Command { ... }` 或目测）
- 镜像最终大小：`docker images criyle/go-judge`、`docker images | findstr sandbox`
- 任何 WARNING / ERROR

第一次 build 涉及拉 `criyle/go-judge:v1.12.0` + Debian apt install
JDK17 + g++ + python3，预计 5-15 分钟（取决于网络）。耐心等。

如果 apt install 卡住超过 10 分钟无进展，记录到 outbox 标 blocked。

输出到 outbox **Section 2: "Build sandbox"**。

============================================================
TASK 3 — 启动 sandbox（仅 sandbox，不启 worker）
============================================================

本 phase 只验证 sandbox 本身，不验证 worker（worker 要 MySQL /
RabbitMQ / Nacos，本任务不依赖那些）。

```powershell
docker compose -f deploy/compose.yml --profile judge up -d sandbox
```

⚠️ 如果 compose 因为 sandbox 没有 healthcheck pass 阻塞 worker 启动
报错，**只启 sandbox 即可**：在 up 命令前先 `docker compose ... up
-d --no-deps sandbox`（跳过依赖检查），然后只观察 sandbox 状态。

等 15-30 秒让 healthcheck 通过，然后：

```powershell
docker compose -f deploy/compose.yml ps
docker compose -f deploy/compose.yml logs --tail=50 sandbox
```

把 ps 输出（重点是 sandbox 的 STATUS 列必须 `Up ... (healthy)`）和
logs 末尾 50 行贴 outbox **Section 3: "Sandbox container status"**。

如果 healthcheck 一直 unhealthy 或 logs 报错，标 blocked + 贴具体
error message。

============================================================
TASK 4 — 拿 go-judge 真实 /version
============================================================

```powershell
curl.exe -i http://localhost:8090/version
```

把 HTTP status + headers + JSON body **全文**贴 outbox
**Section 4: "go-judge /version response"**。

这一步关键产出：拿到 go-judge 准确版本号 + 暴露的 API endpoints
列表（如果 /version 返回 endpoints 清单）。

============================================================
TASK 5 — 查 go-judge /run 真实 schema
============================================================

用 WebFetch 抓 `https://github.com/criyle/go-judge`，找到 README
里关于 `POST /run` 的请求 / 响应 schema 描述。把找到的内容（包括
Request 字段列表 + Response 字段列表 + cmd[] 结构 + copyIn /
copyOut / files / 资源限制单位等）贴 outbox **Section 5: "go-judge
/run schema (from docs)"**。

注意单位（go-judge 通常用 ns / bytes，AIOJ 用 ms / KB）。

============================================================
TASK 6 — Python hello world 实测
============================================================

构造一个最小 Python hello world 请求。基于 Section 5 抓到的 schema
（如果 schema 与本任务下方示例字段不同，**以你抓到的为准**）。

参考请求体（可能需要按实际 schema 微调）：

```json
{
  "cmd": [
    {
      "args": ["/usr/bin/python3", "main.py"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 10240},
        {"name": "stderr", "max": 10240}
      ],
      "cpuLimit": 1000000000,
      "memoryLimit": 268435456,
      "procLimit": 50,
      "copyIn": {
        "main.py": {"content": "print('hello world')"}
      }
    }
  ]
}
```

PowerShell 命令（注意 JSON escape）：

```powershell
$body = @'
{...上面那个 JSON...}
'@
$body | Out-File -Encoding utf8 -FilePath python-hello.json
curl.exe -X POST http://localhost:8090/run -H "Content-Type: application/json" --data-binary "@python-hello.json"
```

或者更简单地用 Invoke-RestMethod。**响应 JSON 必须完整贴 outbox**，
**不要省略任何字段**。

输出到 outbox **Section 6: "Python hello world"**：
- 请求体全文
- 响应体全文
- 关键字段解读（status / time / memory / exitStatus / files.stdout）

============================================================
TASK 7 — C++ 编译 + 运行实测
============================================================

C++ 是 OJ 最关键的场景：需要先 `g++ -O2 main.cpp -o main` 编译，
然后跑 `./main`。go-judge 支持在一次请求里包含多个 cmd（编译 +
执行），或用 `copyOutCached` 把编译产物 cache 起来在下一次请求使
用。**用单请求双 cmd 的写法**（参考 go-judge README）：

```json
{
  "cmd": [
    {
      "args": ["/usr/bin/g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 10240},
        {"name": "stderr", "max": 10240}
      ],
      "cpuLimit": 10000000000,
      "memoryLimit": 536870912,
      "procLimit": 50,
      "copyIn": {
        "main.cpp": {
          "content": "#include<iostream>\nint main(){std::cout<<\"hello cpp\\n\";return 0;}"
        }
      },
      "copyOut": ["stdout", "stderr"],
      "copyOutCached": ["main"]
    },
    {
      "args": ["./main"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 10240},
        {"name": "stderr", "max": 10240}
      ],
      "cpuLimit": 1000000000,
      "memoryLimit": 268435456,
      "procLimit": 50,
      "copyIn": {
        "main": "{{prev.copyOutCached.main}}"
      }
    }
  ]
}
```

⚠️ `{{prev.copyOutCached.main}}` 是占位符；具体 go-judge 是用
`fileId` 引用还是字面占位符要按 Section 5 抓到的 schema 调整。

输出到 outbox **Section 7: "C++ compile + run"**：
- 请求体全文
- 响应体全文
- 编译阶段和执行阶段的响应字段都要贴
- 如果 schema 实际是异步 / token 模式，记录怎么拿最终结果

============================================================
TASK 8 — Java 编译 + 运行实测
============================================================

Java 类似 C++ 但用 javac / java：

```
javac Main.java          # produces Main.class
java -cp . Main          # runs
```

注意 Java class file name 必须匹配源码 public class 名（`Main`）。

写双 cmd 请求，源码：

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("hello java");
    }
}
```

输出到 outbox **Section 8: "Java compile + run"**：
- 请求体全文
- 响应体全文（编译阶段 + 执行阶段）

============================================================
TASK 9 — 异常场景：TLE
============================================================

Python 死循环 + 限时 100ms 看 TLE 响应：

```python
while True: pass
```

请求里 `cpuLimit: 100000000`（100ms in ns）。把响应贴 outbox
**Section 9: "TLE behavior"**。关键看：
- `status` 字段值是什么（"Time Limit Exceeded"？"TLE"？其他？）
- `time` 字段值是否大约等于 100ms
- `exitStatus` 是 -1 / 137 / 还是别的

============================================================
TASK 10 — 异常场景：RE (exit non-zero)
============================================================

Python `exit(1)`：

```python
import sys; sys.exit(1)
```

把响应贴 outbox **Section 10: "Runtime Error behavior"**。看：
- `status` 是 "Nonzero Exit Status" / "Runtime Error" / 还是别的
- `exitStatus` 是 1
- 没有 OOM / TLE 干扰

============================================================
TASK 11 — 异常场景：OLE (output limit exceeded)
============================================================

Python 大量输出 + 设置 stdout max=10240（10KB）：

```python
for _ in range(1000000): print("xxxxxxxxxxxxx")
```

把响应贴 outbox **Section 11: "Output Limit behavior"**。看：
- `status` 是什么
- `files.stdout` 是否被截断到 10KB
- 是否有专门的 OLE 状态

============================================================
TASK 12 — 字段映射表（Phase 2/3 关键输出）
============================================================

基于 TASK 4-11 的实测响应，输出一个**字段映射表**（outbox
**Section 12: "go-judge → AIOJ field mapping"**）。这是 Phase 2/3
DTO 设计的直接输入：

| go-judge 字段 | 类型 / 单位 | AIOJ JudgeResult 字段 | 转换规则 | 备注 |
|---|---|---|---|---|
| `status` | string | `status: SubmissionStatus` | map: "Accepted"→ACCEPTED, "Time Limit Exceeded"→TLE, ... | **列出实测出现的所有 status 字符串** |
| `time` | ns | `timeMillis: long` | / 1_000_000 | wall time vs cpu time 都要列 |
| `memory` | bytes | `memoryKb: long` | / 1024 | 是 max RSS 还是别的 |
| `runTime` | ns | (可选) | | wall time |
| `exitStatus` | int | `exitCode: int` (新增) | direct | |
| `files.stdout` | string | `stdout: string` (新增) | direct | 截断长度 |
| `files.stderr` | string | `stderr: string` (新增) | direct | 截断长度 |
| signal | ? | `signal: int` (新增) | 实测是哪个字段 | |

⚠️ 表里**只写实测看到的字段**。如果某字段你期望存在但实测没出现，
明确标注 "not observed in tests"。

============================================================
TASK 13 — Cleanup（可选）
============================================================

测完不强制 down sandbox。如果要清：

```powershell
docker compose -f deploy/compose.yml --profile judge down
```

在 outbox 记录是否清理 + cleanup 命令。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  docs/codex-exchange/inbox/2026-05-26-1700-sandbox-smoke-test.md
     ?? docs/codex-exchange/outbox/2026-05-26-1700-sandbox-smoke-test.md
   **No other file modified**。本任务是 read-only 实测 + 写报告。
   如果发现需要改 deploy/ 或 docker 文件，停下来标 blocked 并说明
   原因（可能 Phase 1 配置有 bug，由 Claude 决定下一步）。

2. Outbox 必须包含 13 个 Section（1-13）。每个 Section 的响应/日志
   都是**实测原文**，不是描述。

3. 5-sentence Chinese summary：
   - sandbox 在 Windows + Docker Desktop 启动表现
   - go-judge `/run` schema 与 Claude 草稿假设的差异（关键！）
   - 三语言 hello world 是否都成功
   - TLE / RE / OLE 三种异常场景实际响应特征
   - 字段映射表中**最让你 surprised** 的发现（如 status 字符串、
     单位差异、字段命名等）

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1700-sandbox-smoke-test.md`
Then update inbox top `## Status: new` → `## Status: done` (or
`blocked`).

DO NOT commit, stage, push, or modify any project files.

## Constraints / Files in scope

**Touch (write)**:
- This inbox (Status only)
- The outbox (new, 13 sections)
- 可以在 OS temp 目录创建临时 JSON 请求文件（如 python-hello.json），
  跑完测试自删或留 temp 都行，不要污染项目目录

**Read for reference**:
- `deploy/compose.yml`（确认 sandbox 服务定义）
- `deploy/sandbox/Dockerfile`
- `https://github.com/criyle/go-judge` README（API schema）

**Hard 禁区**:
- backend/** / packages/** / apps/**
- 任何 .java / .ts / .vue / .sql
- 修改 deploy/ 任何文件
- commit / stage / push
