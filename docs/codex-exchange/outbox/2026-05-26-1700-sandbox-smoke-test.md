# Task: go-judge 沙箱实测验证（Phase 1 收尾，为 Phase 2/3 提供真实响应字段）
## Status: blocked
## Created by: Codex @ 2026-05-28T09:35+08:00
## Linked: inbox/2026-05-26-1700-sandbox-smoke-test.md

---

## Report

### Section 1: Docker environment

Preflight `git status --short -uall` shows this smoke-test task is being run on top of existing baseline dirty files from earlier exchange tasks. This task only changed the 1700 inbox status and created this 1700 outbox.

Task files:

```text
 M docs/codex-exchange/inbox/2026-05-26-1700-sandbox-smoke-test.md
?? docs/codex-exchange/outbox/2026-05-26-1700-sandbox-smoke-test.md
```

Baseline dirty files observed before this task, preserved and not reverted:

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
 M deploy/compose.yml
 M docs/HANDOVER.md
 M docs/deployment.md
 M package-lock.json
 M packages/i18n/src/messages.ts
?? apps/web-admin/src/components/TestcaseManifestEditor.vue
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? deploy/sandbox/Dockerfile
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
?? docs/codex-exchange/inbox/2026-05-26-1600-sandbox-infra-bootstrap.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
?? docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md
?? docs/codex-exchange/outbox/2026-05-26-1530-sandbox-design-research.md
?? docs/codex-exchange/outbox/2026-05-26-1600-sandbox-infra-bootstrap.md
```

Docker commands:

```text
--- docker --version ---
Docker version 29.5.2, build 79eb04c

--- docker compose version ---
Docker Compose version v5.1.3

--- docker info selected ---
 Server Version: 29.5.2
 Kernel Version: 6.6.114.1-microsoft-standard-WSL2
 Operating System: Docker Desktop
 OSType: linux
```

Temp directory for request/log files:

```text
C:\Users\86133\AppData\Local\Temp\aioj-gojudge-smoke-20260528-093540
```

### Section 2: Build sandbox

First attempted command from the inbox:

```powershell
docker compose -f deploy\compose.yml --profile judge build sandbox
```

Actual output:

```text
docker : service "judge-worker" depends on undefined service "rabbitmq": invalid compose project
At line:8 char:30
+ ... e-Command { docker compose -f deploy\compose.yml --profile judge buil ...
+                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (service "judge-...compose project:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
```

Observation: enabling only `judge` profile makes Compose validate `judge-worker`, whose `depends_on` references `rabbitmq` and `nacos` services that are not enabled. I did not edit `deploy/compose.yml`. To continue testing the sandbox build without changing files, I retried with both `infra` and `judge` profiles enabled:

```powershell
Measure-Command { docker compose -f deploy\compose.yml --profile infra --profile judge build sandbox }
```

Elapsed:

```text
Build retry elapsed: 00:05:52.2552356
```

The retry still failed during Debian apt index download:

```text
#9 [stage-1 2/4] RUN apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*
#9 6.524 Get:1 http://deb.debian.org/debian bookworm InRelease [151 kB]
#9 9.107 Get:2 http://deb.debian.org/debian bookworm-updates InRelease [55.4 kB]
#9 11.94 Get:3 http://deb.debian.org/debian-security bookworm-security InRelease [48.0 kB]
#9 13.12 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 16.04 Get:5 http://deb.debian.org/debian bookworm-updates/main amd64 Packages [6924 B]
#9 20.24 Ign:6 http://deb.debian.org/debian-security bookworm-security/main amd64 Packages
#9 26.84 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 34.03 Get:6 http://deb.debian.org/debian-security bookworm-security/main amd64 Packages [308 kB]
#9 64.07 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 104.1 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 166.1 Err:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 166.1   502  Gateway Error [IP: 151.101.2.132 80]
#9 229.5 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 291.6 Err:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 291.6   502  Gateway Error [IP: 151.101.2.132 80]
#9 293.2 Ign:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 294.6 Err:4 http://deb.debian.org/debian bookworm/main amd64 Packages
#9 294.6   502  Gateway Error [IP: 151.101.2.132 80]
#9 294.6 Fetched 570 kB in 4min 54s (1940 B/s)
#9 294.6 Reading package lists...
#9 294.6 E: Failed to fetch http://deb.debian.org/debian/dists/bookworm/main/binary-amd64/Packages  502  Gateway Error [IP: 151.101.2.132 80]
#9 294.6 E: Some index files failed to download. They have been ignored, or old ones used instead.
#9 ERROR: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not complete successfully: exit code: 100
------
Dockerfile:5

--------------------

   4 |

   5 | >>> RUN apt-get update \

   6 | >>>     && apt-get install -y --no-install-recommends \

   7 | >>>       ca-certificates \

   8 | >>>       g++ \

   9 | >>>       openjdk-17-jdk-headless \

  10 | >>>       python3 \

  11 | >>>       wget \

  12 | >>>     && rm -rf /var/lib/apt/lists/*

  13 |

--------------------

failed to solve: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certific
ates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not compl
ete successfully: exit code: 100

View build details: docker-desktop://dashboard/build/default/default/znds74dlm2y1zuqvnu0jd0z8p
```

Image list after failed build:

```text
--- docker images criyle/go-judge ---
IMAGE   ID             DISK USAGE   CONTENT SIZE   EXTRA
--- docker images | findstr sandbox ---
```

Result: blocked at build. The failure is a network/mirror failure from `deb.debian.org` returning 502 while fetching the Bookworm package index. I stopped before container startup and did not modify `deploy/`.

### Section 3: Sandbox container status

Not executed because Section 2 build failed.

I still checked Compose state after the failed build:

```text
NAME      IMAGE     COMMAND   SERVICE   CREATED   STATUS    PORTS
```

No sandbox container was started.

### Section 4: go-judge `/version` response

Not executed because Section 2 build failed and no sandbox container was available at `localhost:8090`.

Expected command that was not run:

```powershell
curl.exe -i http://localhost:8090/version
```

### Section 5: go-judge `/run` schema from docs

Sources used:
- https://github.com/criyle/go-judge
- https://docs.goj.ac/api

Official REST endpoints from docs:

```text
/run POST execute program in the restricted environment
/file GET list all cached file id to original name map
/file POST prepare a file in the go judge (in memory), returns fileId
/file/:fileId GET downloads file from go judge (in memory), returns file content
/file/:fileId DELETE delete file specified by fileId
/ws WebSocket for /run
/stream WebSocket for stream run
/version gets build git version together with runtime information
/config gets configuration and supported features
```

Request shape excerpt:

```ts
type run = (request: Request) => []Result;

interface Request {
  requestId?: string;
  cmd: Cmd[];
  pipeMapping?: PipeMap[];
}

interface Cmd {
  args: string[];
  env?: string[];
  files?: (LocalFile | MemoryFile | PreparedFile | Collector | StreamIn | StreamOut | null)[];
  tty?: boolean;

  cpuLimit?: number;     // ns
  realCpuLimit?: number; // deprecated
  clockLimit?: number;   // ns
  memoryLimit?: number;  // byte
  stackLimit?: number;   // byte
  procLimit?: number;
  cpuRateLimit?: number;
  cpuSetLimit?: string;
  strictMemoryLimit?: boolean; // deprecated
  dataSegmentLimit?: boolean;
  addressSpaceLimit?: boolean;

  copyIn?: {[dst:string]: LocalFile | MemoryFile | PreparedFile | Symlink};
  copyOut?: string[];
  copyOutCached?: string[];
  copyOutDir?: string;
  copyOutMax?: number; // byte
  copyOutTruncate?: boolean;
}
```

Result shape excerpt:

```ts
interface Result {
  status: Status;
  error?: string;
  exitStatus: number;
  time: number;    // ns, cgroup recorded time
  memory: number;  // byte
  runTime: number; // ns, wall clock time
  procPeak?: number;
  files?: {[name:string]: string};
  fileIds?: {[name:string]: string};
  fileError?: FileError[];
}
```

Documented status strings:

```text
Accepted
Memory Limit Exceeded
Time Limit Exceeded
Output Limit Exceeded
File Error
Nonzero Exit Status
Signalled
Internal Error
```

Important doc notes:
- `time` and `runTime` use nanoseconds.
- `memory`, `memoryLimit`, `copyOutMax`, and collector `max` use bytes.
- `copyOutCached` stores copied-out files in go-judge and returns `fileIds`; later runs can pass `{"fileId": "..."}`
- Cached files should be deleted through `DELETE /file/:fileId` when no longer needed to avoid leakage.

### Section 6: Python hello world

Not executed because Section 2 build failed.

Planned request body:

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

Response body: not observed in tests.

### Section 7: C++ compile + run

Not executed because Section 2 build failed.

Planned approach after reading docs:
- First `/run` command compiles with `/usr/bin/g++`, `copyOutCached: ["main"]`.
- The response should include `fileIds.main`.
- A second `/run` command should pass `copyIn: {"main": {"fileId": "<observed file id>"}}` and execute `./main`.

This is a correction to the Claude draft placeholder `{{prev.copyOutCached.main}}`; the official schema uses `fileIds` and `PreparedFile { fileId }`.

Response body: not observed in tests.

### Section 8: Java compile + run

Not executed because Section 2 build failed.

Planned approach after reading docs:
- First `/run` command compiles with `/usr/bin/javac Main.java`, `copyOutCached: ["Main.class"]`.
- The response should include `fileIds["Main.class"]`.
- A second `/run` command should pass `copyIn: {"Main.class": {"fileId": "<observed file id>"}}` and execute `/usr/bin/java -cp . Main`.

Response body: not observed in tests.

### Section 9: TLE behavior

Not executed because Section 2 build failed.

Planned request body:

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
      "cpuLimit": 100000000,
      "memoryLimit": 268435456,
      "procLimit": 50,
      "copyIn": {
        "main.py": {"content": "while True: pass"}
      }
    }
  ]
}
```

Response body: not observed in tests.

### Section 10: Runtime Error behavior

Not executed because Section 2 build failed.

Planned request body:

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
        "main.py": {"content": "import sys; sys.exit(1)"}
      }
    }
  ]
}
```

Response body: not observed in tests.

### Section 11: Output Limit behavior

Not executed because Section 2 build failed.

Planned request body:

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
        "main.py": {"content": "for _ in range(1000000): print(\"xxxxxxxxxxxxx\")"}
      }
    }
  ]
}
```

Response body: not observed in tests.

### Section 12: go-judge → AIOJ field mapping

Because build failed, this table is based on official docs only plus explicit `not observed in tests` markers. It is not a substitute for Phase 2/3 DTO decisions.

| go-judge 字段 | 类型 / 单位 | AIOJ JudgeResult 字段 | 转换规则 | 备注 |
|---|---|---|---|---|
| `status` | string | `status: SubmissionStatus` | map by observed string | not observed in tests; docs list `Accepted`, `Memory Limit Exceeded`, `Time Limit Exceeded`, `Output Limit Exceeded`, `File Error`, `Nonzero Exit Status`, `Signalled`, `Internal Error` |
| `time` | number / ns | `timeMillis: long` | `/ 1_000_000` | not observed in tests; docs say cgroup recorded time |
| `runTime` | number / ns | optional wall time | `/ 1_000_000` | not observed in tests; docs say wall clock time |
| `memory` | number / byte | `memoryKb: long` | `/ 1024` | not observed in tests |
| `exitStatus` | int | `exitCode: int` | direct | not observed in tests |
| `files.stdout` | string | `stdout: string` | direct | not observed in tests; collector max is bytes |
| `files.stderr` | string | `stderr: string` | direct | not observed in tests; collector max is bytes |
| `fileIds` | object string->string | cached file references | direct storage for follow-up calls | not observed in tests; docs output for `copyOutCached` |
| `fileError` | array | file error diagnostics | preserve details | not observed in tests |
| signal | unknown | `signal: int` | unknown | not observed in docs interface or tests; status may become `Signalled`, but no explicit signal field observed |

### Section 13: Cleanup

No sandbox container was started, so there was no running test service to stop.

Cleanup check:

```powershell
docker compose -f deploy\compose.yml --profile infra --profile judge ps
```

Output:

```text
NAME      IMAGE     COMMAND   SERVICE   CREATED   STATUS    PORTS
```

I did not run `docker compose down` because no Compose service was up.

### Chinese summary

Windows + Docker Desktop 本身可用，Docker client/server 都是 29.5.2，Linux engine 运行在 WSL2；真正阻塞发生在 sandbox image build 阶段。go-judge `/run` 官方 schema 与 Claude 草稿的关键差异是：`copyOutCached` 返回 `fileIds`，后续请求应通过 `PreparedFile { fileId }` 引用，而不是字面 `{{prev.copyOutCached.main}}` 占位符。三语言 hello world 没有执行，因为 Debian `apt-get update` 在安装 JDK17/g++/python3 前被 `deb.debian.org` 的 502 Gateway Error 阻断。TLE / RE / OLE 三种异常场景也没有实测响应，只能先记录官方状态枚举，不能作为 DTO 决策依据。最值得注意的发现有两个：一是只启 `judge` profile 会因为 `judge-worker` 的 `rabbitmq`/`nacos` depends_on 校验失败，二是当前自建 runtime 镜像依赖 Debian 在线 apt 源，国内或瞬时网络不稳会直接阻断 smoke test。

## Next-action hint

- 建议 Claude 决定下一步是“纯重试本 smoke test”还是先开一个小任务处理 compose profile / apt mirror 稳定性。
- 如果只想拿 go-judge `/version` 与基础 `/run` 字段，可考虑临时直接运行官方 `criyle/go-judge:v1.12.0` 镜像；但三语言编译/解释仍需要 runtime image 成功构建。
