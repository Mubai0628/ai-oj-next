# Task: 用官方 go-judge image 跑最小 smoke test，解锁 Phase 2/3 DTO 设计
## Status: done
## Created by: Codex @ 2026-05-28T11:12+08:00
## Linked: inbox/2026-05-26-1900-sandbox-minimal-smoke-test.md

---

## Report

### Section 1: Pull official image

Command:

```powershell
docker pull criyle/go-judge:v1.12.0
docker images criyle/go-judge:v1.12.0
```

Output:

```text
Pull elapsed: 00:00:59.6535931

--- docker images criyle/go-judge:v1.12.0 ---
IMAGE                     ID             DISK USAGE   CONTENT SIZE   EXTRA
criyle/go-judge:v1.12.0   3af6806b4f58        216MB         60.6MB
```

### Section 2: Image contents inspection

Commands:

```powershell
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "ls -la /bin /usr/bin 2>/dev/null | head -100"
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "which sh echo cat ls true false sleep yes head kill chmod 2>/dev/null"
docker run --rm --entrypoint sh criyle/go-judge:v1.12.0 -c "cat /etc/os-release 2>/dev/null"
docker inspect criyle/go-judge:v1.12.0 --format='{{json .Config}}'
```

Key output:

```text
--- ls /bin /usr/bin ---
lrwxrwxrwx 1 root root     7 Mar  2 21:50 /bin -> usr/bin

/usr/bin:
total 25916
drwxr-xr-x  2 root root     12288 Apr 21 00:00 .
drwxr-xr-x 12 root root      4096 Apr 21 00:00 ..
-rwxr-xr-x  1 root root     55720 Jun  4  2025 [
-rwxr-xr-x  1 root root     18752 Jun 24  2025 apt
-rwxr-xr-x  1 root root     92552 Jun 24  2025 apt-cache
-rwxr-xr-x  1 root root     27016 Jun 24  2025 apt-cdrom
-rwxr-xr-x  1 root root     31040 Jun 24  2025 apt-config
-rwxr-xr-x  1 root root     59784 Jun 24  2025 apt-get
-rwxr-xr-x  1 root root     63880 Jun 24  2025 apt-mark
-rwxr-xr-x  1 root root     43464 Jun  4  2025 arch
lrwxrwxrwx  1 root root        21 Feb  4  2025 awk -> /etc/alternatives/awk
-rwxr-xr-x  1 root root   1298416 Mar  8 15:21 bash
-rwxr-xr-x  1 root root     47592 Jun  4  2025 cat
-rwxr-xr-x  1 root root     72136 Jun  4  2025 chmod
-rwxr-xr-x  1 root root    129736 Feb  4  2025 dash
-rwxr-xr-x  1 root root     43432 Jun  4  2025 false
-rwxr-xr-x  1 root root     51656 Jun  4  2025 head
```

Available tools:

```text
/usr/bin/sh
/usr/bin/echo
/usr/bin/cat
/usr/bin/ls
/usr/bin/true
/usr/bin/false
/usr/bin/sleep
/usr/bin/yes
/usr/bin/head
/usr/bin/chmod
```

OS:

```text
PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
NAME="Debian GNU/Linux"
VERSION_ID="13"
VERSION="13 (trixie)"
VERSION_CODENAME=trixie
DEBIAN_VERSION_FULL=13.4
ID=debian
```

Docker config:

```json
{"Env":["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],"Entrypoint":["/opt/go-judge"],"WorkingDir":"/opt"}
```

### Section 3: Run official sandbox

Command:

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

Output:

```text
--- docker run ---
e5c7ad2071a659a4d927db93fe2e9e1da273d6ca533cf7bc62f187bb1b6ca05b
--- docker ps ---
CONTAINER ID   IMAGE                     COMMAND           CREATED          STATUS          PORTS                      NAMES
e5c7ad2071a6   criyle/go-judge:v1.12.0   "/opt/go-judge"   12 seconds ago   Up 10 seconds   127.0.0.1:8090->8090/tcp   aioj-sandbox-minimal
```

Logs:

```text
{"level":"info","ts":1779937281.4126885,"caller":"go-judge/main.go:62","msg":"Config loaded","config":"&{ContainerInitPath: PreFork:1 TmpFsParam:size=128m,nr_inodes=4k NetShare:false MountConf:mount.yaml SeccompConf:seccomp.yaml Parallelism:16 CgroupPrefix:gojudge ContainerCredStart:0 NoFallback:false SrcPrefix:[] Dir: TimeLimitCheckerInterval:100ms ExtraMemoryLimit:16.0 KiB OutputLimit:256.0 MiB CopyOutLimit:256.0 MiB OpenFileLimit:256 Cpuset:[] EnableCPURate:false CPUCfsPeriod:100ms FileTimeout:0s HTTPAddr::8090 EnableGRPC:false GRPCAddr::5051 MonitorAddr::5052 AuthToken: GRPCMsgSize:64.0 MiB EnableDebug:false EnableMetrics:false Release:true Silent:false ForceGCTarget:20.0 MiB ForceGCInterval:5s Version:false}"}
{"level":"info","ts":1779937281.4158034,"caller":"env/env_linux.go:129","msg":"created container mount","mountBuilder":"Mounts: bind[/bin:bin:ro], bind[/lib:lib:ro], bind[/lib64:lib64:ro], bind[/usr:usr:ro], bind[/etc/ld.so.cache:etc/ld.so.cache:ro], bind[/etc/alternatives:etc/alternatives:ro], bind[/etc/fpc.cfg:etc/fpc.cfg:ro], bind[/etc/mono:etc/mono:ro], bind[/var/lib/ghc:var/lib/ghc:ro], bind[/dev/null:dev/null:rw], bind[/etc/java-21-openjdk:etc/java-21-openjdk:ro], bind[/dev/urandom:dev/urandom:rw], bind[/dev/random:dev/random:rw], bind[/dev/zero:dev/zero:rw], bind[/dev/full:dev/full:rw], tmpfs[w], tmpfs[tmp], bind[/opt/containerPasswd.txt:etc/passwd:rw], bind[/opt/dotenv:.env:rw], proc[ro]"}
{"level":"info","ts":1779937281.4175265,"caller":"env/env_linux.go:199","msg":"creating container builder","host_name":"go-judge","domain_name":"go-judge","work_dir":"/w"}
{"level":"info","ts":1779937281.417825,"caller":"env/env_cgroup_linux.go:36","msg":"running with cgroup v2, connecting systemd dbus to create cgroup"}
{"level":"info","ts":1779937281.418864,"caller":"env/env_cgroup_linux.go:39","msg":"connecting to systemd dbus failed, falling back to current cgroup prefix","error":"dial unix /var/run/dbus/system_bus_socket: connect: no such file or directory"}
{"level":"info","ts":1779937281.4189262,"caller":"env/env_cgroup_linux.go:47","msg":"using current cgroup prefix for nesting","scope_name":""}
{"level":"info","ts":1779937281.4189749,"caller":"env/env_cgroup_linux.go:126","msg":"creating nesting api cgroup","cgroup":"v2(/sys/fs/cgroup)[cpu, cpuset, memory, pids]"}
{"level":"info","ts":1779937281.4537504,"caller":"env/env_cgroup_linux.go:138","msg":"creating containers cgroup"}
{"level":"info","ts":1779937281.454247,"caller":"env/env_linux.go:221","msg":"running kernel >= 5.7 with cgroup V2, trying faster clone3(CLONE_INTO_CGROUP)","major":6,"minor":6}
{"level":"info","ts":1779937281.4720037,"caller":"go-judge/main.go:298","msg":"Create prefork containers","count":1}
{"level":"info","ts":1779937281.483806,"caller":"go-judge/main.go:74","msg":"Worker stated ","parallelism":16,"dir":"/dev/shm/go-judge","timeLimitCheckInterval":0.1}
{"level":"info","ts":1779937281.488335,"caller":"go-judge/main.go:208","msg":"Starting http server","addr":":8090","listener":"[::]:8090"}
```

### Section 4: `/version` & `/config`

Commands:

```powershell
curl.exe -s -i http://localhost:8090/version
curl.exe -s -i http://localhost:8090/config
```

`/version` response:

```text
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Date: Thu, 28 May 2026 03:01:42 GMT
Content-Length: 263

{"addressSpaceLimit":true,"buildVersion":"v1.12.0","copyOutOptional":true,"copyOutTruncate":true,"fixSymlinkEscape":true,"goVersion":"go1.25.9","os":"linux","pipeProxy":true,"pipeProxyZeroCopy":true,"platform":"amd64","procPeak":true,"stream":true,"symlink":true}
```

`/config` response:

```text
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Date: Thu, 28 May 2026 03:01:42 GMT
Content-Length: 2037

{"addressSpaceLimit":true,"copyOutOptional":true,"copyOutTruncate":true,"fileStorePath":"/dev/shm/go-judge","fixSymlinkEscape":true,"pipeProxy":true,"pipeProxyZeroCopy":true,"procPeak":true,"runnerConfig":{"cgroupControllers":["cpu","cpuset","memory","pids"],"cgroupType":2,"clone3":true,"domainName":"go-judge","gid":1536,"hostName":"go-judge","maskedPaths":["/sys/firmware","/sys/devices/virtual/powercap","/proc/acpi","/proc/asound","/proc/kcore","/proc/keys","/proc/latency_stats","/proc/timer_list","/proc/timer_stats","/proc/sched_debug","/proc/scsi","/usr/lib/wsl/drivers","/usr/lib/wsl/lib"],"mount":[{"Source":"/bin","Target":"bin","FsType":"","Data":"","Flags":282627},{"Source":"/lib","Target":"lib","FsType":"","Data":"","Flags":282627},{"Source":"/lib64","Target":"lib64","FsType":"","Data":"","Flags":282627},{"Source":"/usr","Target":"usr","FsType":"","Data":"","Flags":282627},{"Source":"/etc/ld.so.cache","Target":"etc/ld.so.cache","FsType":"","Data":"","Flags":282627},{"Source":"/etc/alternatives","Target":"etc/alternatives","FsType":"","Data":"","Flags":282627},{"Source":"/dev/null","Target":"dev/null","FsType":"","Data":"","Flags":282626},{"Source":"/dev/urandom","Target":"dev/urandom","FsType":"","Data":"","Flags":282626},{"Source":"/dev/random","Target":"dev/random","FsType":"","Data":"","Flags":282626},{"Source":"/dev/zero","Target":"dev/zero","FsType":"","Data":"","Flags":282626},{"Source":"/dev/full","Target":"dev/full","FsType":"","Data":"","Flags":282626},{"Source":"tmpfs","Target":"w","FsType":"tmpfs","Data":"size=128m,nr_inodes=4k","Flags":1030},{"Source":"tmpfs","Target":"tmp","FsType":"tmpfs","Data":"size=128m,nr_inodes=4k","Flags":1030},{"Source":"proc","Target":"proc","FsType":"proc","Data":"","Flags":15}],"symbolicLink":[{"LinkPath":"/dev/fd","Target":"/proc/self/fd"},{"LinkPath":"/dev/stdin","Target":"/proc/self/fd/0"},{"LinkPath":"/dev/stdout","Target":"/proc/self/fd/1"},{"LinkPath":"/dev/stderr","Target":"/proc/self/fd/2"}],"uid":1536,"workDir":"/w"},"stream":true,"symlink":true}
```

### Section 5: Minimal `/run` (echo)

Request body:

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

Response body:

```json
[{"status":"Accepted","exitStatus":0,"time":1042000,"memory":294912,"runTime":975350,"procPeak":1,"files":{"stderr":"","stdout":"hello-from-sandbox\n"}}]
```

Key fields:

```text
status=Accepted
exitStatus=0
time=1042000 ns
memory=294912 bytes
runTime=975350 ns
procPeak=1
files.stdout="hello-from-sandbox\n"
files.stderr=""
```

### Section 6: TLE behavior

Request body:

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

Response body:

```json
[{"status":"Time Limit Exceeded","exitStatus":9,"time":101370000,"memory":262144,"runTime":101189459,"procPeak":1,"files":{"stderr":"","stdout":""}}]
```

Key fields:

```text
status="Time Limit Exceeded"
exitStatus=9
time=101370000 ns, close to the 100000000 ns cpuLimit
runTime=101189459 ns
memory=262144 bytes
```

### Section 7: Runtime Error (non-zero exit)

Request body:

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

Response body:

```json
[{"status":"Nonzero Exit Status","exitStatus":7,"time":1405000,"memory":262144,"runTime":645133,"procPeak":1,"files":{"stderr":"","stdout":""}}]
```

Key fields:

```text
status="Nonzero Exit Status"
exitStatus=7
```

### Section 8: Signalled behavior

Request body:

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

Response body:

```json
[{"status":"Signalled","exitStatus":11,"time":7018000,"memory":516096,"runTime":12890657,"procPeak":1,"files":{"stderr":"","stdout":""}}]
```

Key fields:

```text
status="Signalled"
exitStatus=11
No separate signal field appeared in the response.
```

### Section 9: Output Limit Exceeded

First request used the inbox body with `yes` and `memoryLimit=67108864`.

Request body:

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

Response body:

```json
[{"status":"Memory Limit Exceeded","exitStatus":1,"time":161546000,"memory":67125248,"runTime":837702411,"procPeak":2,"files":{"stderr":"","stdout":"xxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxx"},"fileError":[{"name":"stdout","type":"CollectSizeExceeded","message":"Output Limit Exceeded"}]}]
```

Observation: this request hit output collection limit but final `status` was `Memory Limit Exceeded`, likely because unbounded `yes` plus output buffering also reached memory limit. `fileError` still contained the output-limit signal.

Second request used finite output with higher memory to isolate OLE.

Request body:

```json
{
  "cmd": [
    {
      "args": ["/bin/sh", "-c", "yes xxxxxxxxxxxxx | head -c 2048"],
      "env": ["PATH=/usr/bin:/bin"],
      "files": [
        {"content": ""},
        {"name": "stdout", "max": 1024},
        {"name": "stderr", "max": 1024}
      ],
      "cpuLimit": 1000000000,
      "memoryLimit": 536870912,
      "procLimit": 50
    }
  ]
}
```

Response body:

```json
[{"status":"Output Limit Exceeded","exitStatus":0,"error":"Output Limit Exceeded","time":3254000,"memory":1007616,"runTime":2289217,"procPeak":3,"files":{"stderr":"","stdout":"xxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxxxxxxxxxxxx\nxxx"},"fileError":[{"name":"stdout","type":"CollectSizeExceeded","message":"Output Limit Exceeded"}]}]
```

Key fields:

```text
status="Output Limit Exceeded"
exitStatus=0
error="Output Limit Exceeded"
fileError[0].name="stdout"
fileError[0].type="CollectSizeExceeded"
fileError[0].message="Output Limit Exceeded"
files.stdout is truncated to the collector max.
```

### Section 10: MLE behavior (best effort)

Request body:

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

Response body:

```json
[{"status":"Memory Limit Exceeded","exitStatus":9,"time":188820000,"memory":16793600,"runTime":122689555,"procPeak":3,"files":{"stderr":"","stdout":""}}]
```

Key fields:

```text
status="Memory Limit Exceeded"
exitStatus=9
memory=16793600 bytes, close to memoryLimit 16777216 + small overhead
procPeak=3
```

### Section 11: fileId compile-cache simulation

Create cached runner request:

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

Create cached runner response:

```json
[{"status":"Accepted","exitStatus":0,"time":1791000,"memory":524288,"runTime":5776895,"procPeak":2,"files":{"stderr":"","stdout":""},"fileIds":{"runner":"L4CMA7FZ"}}]
```

Observed fileId:

```text
L4CMA7FZ
```

Run cached runner request:

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
        "runner": {"fileId": "L4CMA7FZ"}
      }
    }
  ]
}
```

Run cached runner response:

```json
[{"status":"Accepted","exitStatus":0,"time":674000,"memory":262144,"runTime":601231,"procPeak":1,"files":{"stderr":"","stdout":"cached-ok\n"}}]
```

Cleanup command:

```powershell
curl.exe -X DELETE http://localhost:8090/file/L4CMA7FZ
```

Output:

```text
<empty body>
```

Key fields:

```text
fileIds field shape: {"runner":"L4CMA7FZ"}
fileId length: 8
fileId charset observed: uppercase letters + digits
PreparedFile copyIn shape worked: {"runner":{"fileId":"L4CMA7FZ"}}
```

### Section 12: go-judge → AIOJ field mapping (verified)

| go-judge 字段 | 实测值示例 | 类型 / 单位 | 备注 |
|---|---|---|---|
| `status` | `"Accepted"`, `"Time Limit Exceeded"`, `"Nonzero Exit Status"`, `"Signalled"`, `"Memory Limit Exceeded"`, `"Output Limit Exceeded"` | string | 本轮实测 6 种 status |
| `time` | `1042000`, `101370000`, `188820000` | number / ns | cgroup recorded time；TLE 接近 `cpuLimit` |
| `memory` | `294912`, `67125248`, `16793600` | number / bytes | MLE 时接近 `memoryLimit` |
| `runTime` | `975350`, `101189459`, `837702411` | number / ns | wall clock；输出场景可能明显大于 `time` |
| `exitStatus` | `0`, `7`, `9`, `11` | int | non-zero exit 保持原 exit code；signal 11 写入 `exitStatus` |
| `files.stdout` | `"hello-from-sandbox\n"`, `"cached-ok\n"` | string | collector 输出；超限时会被截断 |
| `files.stderr` | `""` | string | 本轮均为空字符串 |
| `procPeak` | `1`, `2`, `3` | number | `/version` 显示支持 `procPeak=true`，每次响应均出现 |
| `error` | `"Output Limit Exceeded"` | string | 只在隔离出的 OLE 响应中出现 |
| `fileError` | `[{"name":"stdout","type":"CollectSizeExceeded","message":"Output Limit Exceeded"}]` | array | OLE 与“输出超限但最终 MLE”的响应都出现 |
| `fileIds.<name>` | `"L4CMA7FZ"` | string | `copyOutCached` 返回；后续用 `copyIn.<name>.fileId` 引用 |
| signal field | not observed | n/a | Signalled 响应无单独 `signal` 字段；信号值出现在 `exitStatus=11` |

Status 字符串集合：

```text
Accepted
Time Limit Exceeded
Nonzero Exit Status
Signalled
Memory Limit Exceeded
Output Limit Exceeded
```

Phase 2/3 notes:

```text
1. time/runTime are nanoseconds; AIOJ should convert to ms carefully, probably ceiling for display.
2. memory is bytes; AIOJ memoryKb = ceil(bytes / 1024) is safer than floor.
3. Output limit has both status="Output Limit Exceeded" and fileError.type="CollectSizeExceeded" when isolated.
4. An unbounded output command can end as Memory Limit Exceeded while still carrying fileError CollectSizeExceeded.
5. Signalled has no separate signal field in this response shape; use status + exitStatus.
```

### Section 13: Cleanup

Commands:

```powershell
docker stop aioj-sandbox-minimal
docker rm aioj-sandbox-minimal
docker ps -a --filter name=aioj-sandbox-minimal
```

Output:

```text
--- docker stop ---
aioj-sandbox-minimal
--- docker rm ---
aioj-sandbox-minimal
--- docker ps after cleanup ---
CONTAINER ID   IMAGE     COMMAND   CREATED   STATUS    PORTS     NAMES
```

### Verification

Expected task files:

```text
 M docs/codex-exchange/inbox/2026-05-26-1900-sandbox-minimal-smoke-test.md
?? docs/codex-exchange/outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md
```

No source file was modified by this task. Existing baseline dirty files from earlier exchange tasks remain in the working tree and were not reverted.

### Chinese summary

官方 `criyle/go-judge:v1.12.0` image 在 Windows + Docker Desktop 上启动成功，`/version` 和 `/config` 均返回 200，容器使用 cgroup v2 并监听 `127.0.0.1:8090`。本轮实测 status 字符串集合为 `Accepted`、`Time Limit Exceeded`、`Nonzero Exit Status`、`Signalled`、`Memory Limit Exceeded`、`Output Limit Exceeded`。fileId cache 机制按文档工作：`copyOutCached` 返回 `fileIds.runner = "L4CMA7FZ"`，后续 `copyIn.runner.fileId` 可以正常执行并输出 `cached-ok\n`。实际响应中稳定存在 `status`、`exitStatus`、`time`、`memory`、`runTime`、`procPeak`、`files`，OLE 场景额外出现 `error` 和 `fileError`，Signalled 场景没有单独 `signal` 字段。Phase 2/3 DTO 和状态映射已经可以基于本轮数据展开，多语言 runtime 的 apt 构建问题可以作为独立镜像交付问题处理。

## Next-action hint

- Phase 3 状态映射应同时看 `status` 和 `fileError`，因为 unbounded output 场景可能最终 status 是 MLE，但 fileError 已经记录了 Output Limit。
- 建议 JudgeResult 保留 `exitStatus`、`runTimeMillis`、`stdout`、`stderr`、`fileErrors`，否则调试体验会丢掉关键信息。
