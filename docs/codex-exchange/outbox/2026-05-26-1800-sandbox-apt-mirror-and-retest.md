# Task: 修复 sandbox build 网络阻塞 + 绕过 compose 校验 + 重跑 smoke test
## Status: blocked
## Created by: Codex @ 2026-05-28T10:35+08:00
## Linked: inbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md

---

## Report

### Section 1: Dockerfile apt mirror patch

按任务要求只修改了 `deploy/sandbox/Dockerfile` 的 apt 源替换逻辑，未修改 `deploy/compose.yml` 或任何 Java / TS / Vue / SQL / apps / packages 文件。

实际尝试顺序：

```text
1. TUNA:  http://mirrors.tuna.tsinghua.edu.cn
2. Aliyun: http://mirrors.aliyun.com
3. USTC:  http://mirrors.ustc.edu.cn
4. 163:   http://mirrors.163.com
```

最终 Dockerfile 保留最后一次尝试的网易源：

```dockerfile
FROM criyle/go-judge:v1.12.0 AS go-judge

FROM debian:bookworm-slim

# 用网易 Debian 镜像替换默认 deb.debian.org，
# 避免国内 502 / 拉取慢。先用 HTTP（避免 ca-certificates 鸡生蛋
# 问题），等 ca-certificates 装上后下游可自行升级 HTTPS。
# Debian 12 (bookworm) 默认使用 deb822 格式：/etc/apt/sources.list.d/debian.sources
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list.d/debian.sources; \
        sed -i 's|http://security.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list.d/debian.sources; \
    fi; \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list; \
        sed -i 's|http://security.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list; \
    fi
```

### Section 2: Build sandbox (direct docker build)

使用直接 `docker build`，没有走 Compose：

```powershell
docker build --no-cache -t aioj-sandbox:smoke -f deploy/sandbox/Dockerfile deploy/sandbox
```

#### Attempt 1: TUNA

耗时：

```text
Build elapsed: 00:02:49.7261290
```

实测失败日志节选：

```text
#8 147.6 Fetched 199 MB in 2min 19s (1439 kB/s)
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/libt/libtirpc/libtirpc3_1.3.3%2bds-1_amd64.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/libn/libnsl/libnsl2_1.3.0-2_amd64.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/g/gcc-defaults/gcc_12.2.0-3_amd64.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/j/java-common/java-common_0.74_all.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/a/avahi/libavahi-common-data_0.8-10%2bdeb12u1_amd64.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Failed to fetch http://mirrors.tuna.tsinghua.edu.cn/debian/pool/main/b/brotli/libbrotli1_1.0.9-2%2bb6_amd64.deb  503  Service Unavailable [IP: 101.6.15.130 80]
#8 147.6 E: Unable to fetch some archives, maybe run apt-get update or try with --fix-missing?
#8 ERROR: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not complete successfully: exit code: 100
```

#### Attempt 2: Aliyun

耗时：

```text
Build elapsed: 00:11:21.7266707
```

实测失败日志节选：

```text
#8 672.5 Fetched 84.4 MB in 11min 1s (128 kB/s)
#8 672.5 E: Failed to fetch http://mirrors.aliyun.com/debian-security/pool/updates/main/o/openjdk-17/openjdk-17-jre-headless_17.0.19%2b10-1%7edeb12u2_amd64.deb  Connection failed [IP: 124.238.242.15 80]
#8 672.5 E: Failed to fetch http://mirrors.aliyun.com/debian-security/pool/updates/main/o/openjdk-17/openjdk-17-jdk-headless_17.0.19%2b10-1%7edeb12u2_amd64.deb  Connection failed [IP: 124.238.242.14 80]
#8 672.5 E: Unable to fetch some archives, maybe run apt-get update or try with --fix-missing?
#8 ERROR: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not complete successfully: exit code: 100
```

#### Attempt 3: USTC

耗时：

```text
Build elapsed: 00:01:58.5490667
```

实测失败日志节选：

```text
#8 100.3 Fetched 200 MB in 1min 34s (2131 kB/s)
#8 100.3 E: Failed to fetch http://mirrors.ustc.edu.cn/debian/pool/main/libt/libtirpc/libtirpc-common_1.3.3%2bds-1_all.deb  503  Service Unavailable [IP: 202.141.160.110 80]
#8 100.3 E: Failed to fetch http://mirrors.ustc.edu.cn/debian/pool/main/libx/libxcrypt/libcrypt-dev_4.4.33-2_amd64.deb  503  Service Unavailable [IP: 202.141.160.110 80]
#8 100.3 E: Failed to fetch http://mirrors.ustc.edu.cn/debian/pool/main/libt/libtirpc/libtirpc-dev_1.3.3%2bds-1_amd64.deb  503  Service Unavailable [IP: 202.141.160.110 80]
#8 100.3 E: Unable to fetch some archives, maybe run apt-get update or try with --fix-missing?
#8 ERROR: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not complete successfully: exit code: 100
```

#### Attempt 4: 163

耗时：

```text
Build elapsed: 00:09:51.5061556
```

实测失败日志节选：

```text
#8 582.3 Fetched 128 MB in 9min 14s (231 kB/s)
#8 582.3 E: Failed to fetch http://mirrors.163.com/debian/pool/main/p/python3-defaults/python3-minimal_3.11.2-1%2bb1_amd64.deb  503  Service Unavailable [IP: 117.147.202.12 80]
#8 582.3 E: Failed to fetch http://mirrors.163.com/debian/pool/main/g/glibc/libc-dev-bin_2.36-9%2bdeb12u14_amd64.deb  503  Service Unavailable [IP: 117.147.202.12 80]
#8 582.3 E: Failed to fetch http://mirrors.163.com/debian-security/pool/updates/main/o/openjdk-17/openjdk-17-jdk-headless_17.0.19%2b10-1%7edeb12u2_amd64.deb  Connection failed [IP: 117.147.202.12 80]
#8 582.3 E: Unable to fetch some archives, maybe run apt-get update or try with --fix-missing?
#8 ERROR: process "/bin/sh -c apt-get update     && apt-get install -y --no-install-recommends       ca-certificates       g++       openjdk-17-jdk-headless       python3       wget     && rm -rf /var/lib/apt/lists/*" did not complete successfully: exit code: 100
```

Image check after all attempts:

```text
--- docker images aioj-sandbox:smoke ---
IMAGE   ID             DISK USAGE   CONTENT SIZE   EXTRA
```

Result: blocked at direct Docker build. No `aioj-sandbox:smoke` image was produced.

### Section 3: Run sandbox & `/version` response

Not executed because Section 2 never produced `aioj-sandbox:smoke`.

Cleanup/status check for the expected container name:

```powershell
docker ps -a --filter name=aioj-sandbox-smoke --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
```

Output:

```text
NAMES     IMAGE     STATUS    PORTS
```

No sandbox container was started, so `/version` could not be queried.

### Section 4: Python hello world

Not executed because Section 2 build failed and Section 3 did not start a sandbox.

Planned request body from the 1700 smoke test remains:

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

### Section 5: C++ compile + run

Not executed because Section 2 build failed.

Planned mode, based on the 1700 schema review:

```text
1. POST /run with /usr/bin/g++ and copyOutCached: ["main"]
2. Parse response fileIds.main
3. POST /run with copyIn: {"main": {"fileId": "<observed file id>"}}
4. DELETE /file/<fileId>
```

Response body: not observed in tests.

### Section 6: Java compile + run

Not executed because Section 2 build failed.

Planned mode:

```text
1. POST /run with /usr/bin/javac Main.java and copyOutCached: ["Main.class"]
2. Parse response fileIds["Main.class"]
3. POST /run with copyIn: {"Main.class": {"fileId": "<observed file id>"}}
4. DELETE /file/<fileId>
```

Response body: not observed in tests.

### Section 7: TLE behavior

Not executed because Section 2 build failed.

Planned Python source:

```python
while True: pass
```

Response body: not observed in tests.

### Section 8: Runtime Error behavior

Not executed because Section 2 build failed.

Planned Python source:

```python
import sys; sys.exit(1)
```

Response body: not observed in tests.

### Section 9: Output Limit behavior

Not executed because Section 2 build failed.

Planned Python source:

```python
for _ in range(1000000): print("xxxxxxxxxxxxx")
```

Response body: not observed in tests.

### Section 10: go-judge → AIOJ field mapping (from real test data)

No real go-judge `/run` data was produced because the sandbox image could not be built.

| go-judge 字段 | 实测值示例 | 类型 / 单位 | AIOJ JudgeResult 字段 | 转换规则 |
|---|---|---|---|---|
| `status` | not observed in tests | string | `status: SubmissionStatus` | cannot map from this run |
| `time` | not observed in tests | ns | `timeMillis: long` | expected `/ 1_000_000`, not verified |
| `memory` | not observed in tests | byte | `memoryKb: long` | expected `/ 1024`, not verified |
| `runTime` | not observed in tests | ns wall time | optional | expected `/ 1_000_000`, not verified |
| `exitStatus` | not observed in tests | int | `exitCode: int` | direct, not verified |
| `files.stdout` | not observed in tests | string | `stdout: string` | direct, not verified |
| `files.stderr` | not observed in tests | string | `stderr: string` | direct, not verified |
| `fileIds` | not observed in tests | object | cached compiled artifacts | official docs say use `PreparedFile { fileId }`, but not verified in runtime |

实测出现的 status 字符串值：

```text
none; no /run response observed
```

### Section 11: Cleanup

No container was started and no image was produced, so there was nothing to stop/remove.

Commands effectively checked:

```powershell
docker ps -a --filter name=aioj-sandbox-smoke --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
```

Output:

```text
NAMES     IMAGE     STATUS    PORTS
```

### Verification

Final task-intended status:

```text
 M deploy/sandbox/Dockerfile
 M docs/codex-exchange/inbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md
?? docs/codex-exchange/outbox/2026-05-26-1800-sandbox-apt-mirror-and-retest.md
```

There are existing baseline dirty files from earlier exchange tasks; they were preserved and not reverted. This task did not modify source code outside `deploy/sandbox/Dockerfile`.

### Chinese summary

本次先后尝试了 TUNA、阿里云、中科大、网易四个 Debian apt mirror，最终 Dockerfile 保留最后一次尝试的网易源，但没有任何 mirror 能完成 `openjdk-17-jdk-headless` / `g++` / `python3` 的完整安装。sandbox image build 分别耗时约 2:49、11:21、1:58、9:51，全部在 `apt-get install` 阶段因 503 或连接失败而退出 100。三语言 hello world 没有执行，编译产物 `fileId` 模式也未能 runtime 验证。TLE / RE / OLE 均未执行，因此没有实测 `status` 字符串。Phase 2/3 最需要关注的发现是：问题不只是默认源，四个国内 HTTP mirror 都出现包级 503/连接失败，下一步可能需要离线/预构建 runtime image、为 apt 增加重试与 `--fix-missing` 策略，或先拆出“只运行官方 go-judge 镜像获取 `/version` 与最小 `/run` 字段”的轻量 smoke test。

## Next-action hint

- 建议下一轮不要继续单纯换 mirror；更稳的方向是预构建并推送 `aioj-sandbox-runtime` 镜像，或把 Dockerfile 改成带 apt retry / retry-delay / fix-missing 的鲁棒安装。
- 若只是为了先拿 go-judge 响应字段，可以先临时用官方 `criyle/go-judge:v1.12.0` 跑不依赖 Python/JDK/g++ 的 `/version` 和 shell/busybox 类最小测试，再单独处理多语言 runtime。
