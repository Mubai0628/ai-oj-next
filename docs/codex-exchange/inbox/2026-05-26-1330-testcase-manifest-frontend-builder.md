# Task: 测试包上传 UX 改造 —— 前端表单生成 manifest.json，自动注入 zip
## Status: done
## Created by: Claude @ 2026-05-26T13:30+08:00
## Linked: outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md

---

## Prompt

ROLE
You are Codex executing a moderate-size UX improvement task designed
by Claude. Read the entire file before starting. Write your report to
`docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md`
per `docs/codex-exchange/README.md` template. Update this inbox's
`## Status: new` → `## Status: done` when finished.

CONTEXT — user pain & system contract
管理员每次上传测试包都要手工在 zip 内塞一个 `manifest.json`，否则后端
拒绝 (`Testcase package manifest.json is required`)。这对教师很不友好
——他们准备好 case 文件就该能上传。

后端 `TestcasePackageValidator.ManifestPayload` 当前 schema：

```jsonc
{
  "version": "string (max 64, required)",
  "cases": [
    {
      "name":   "string (max 160, optional, defaults to case-N)",
      "input":  "path/to/input/inside/zip",      // required, must exist as file
      "output": "path/to/output/inside/zip",     // required, must exist as file
      "sample": true | false,                    // optional
      "score":  integer                          // optional
    },
    ...
  ]
}
```

后端不会再变。本任务是让前端：
1. 用户选 zip → 前端解析文件列表
2. **自动按命名规则配对 input/output**，渲染可编辑列表
3. 用户调整：version、每条 case 的 sample 标记、可选 score、可微调 name
4. 提交时前端把生成的 `manifest.json` **注入到 zip 内**，再重新计算
   sha256 / 切片，走原有 init → chunks → complete 流程
5. Power-user 工作流保留：如果用户选的 zip **已经包含 manifest.json**，
   跳过表单，直接用现有 zip 上传（不重打包）

CURRENT STATE
- `apps/web-admin/src/components/TestcasePackageUploader.vue` 已实现
  完整分块上传流程。每次都从 `selectedFile` 计算 sha256 切片并 init。
- 修改点：在「选文件」与「上传」之间插入「manifest 配置」一步。
- 后端无需改。

CONSTRAINTS (hard)
- 改前端为主。需要新增 npm 依赖 `jszip`（最小可用 zip 处理库）。
- 不动后端、api-client `submission`/`testcase` endpoints、V*.sql。
- 不破坏现有 power-user 路径（zip 内已有 manifest.json 时跳过表单）。
- 不修改 `TestcasePackageValidator` 行为或测试包后端逻辑。
- 不 commit / stage / push.

============================================================
TASK 1 — Add jszip dependency
============================================================

In `apps/web-admin/package.json`, add to `dependencies`:

```json
"jszip": "^3.10.1"
```

Then run `npm install -w @aioj/web-admin` (or root `npm install`).
Confirm `package-lock.json` updates only show the jszip subgraph and
nothing else unexpected.

If 项目根 package-lock 控制依赖（npm workspaces），update root
package-lock by running `npm install` at root.

============================================================
TASK 2 — Extract a child component: TestcaseManifestEditor.vue
============================================================

Create `apps/web-admin/src/components/TestcaseManifestEditor.vue`.

**Props**:
- `entries: { path: string; sizeBytes: number; isDir: boolean }[]`
  (zip 内文件列表，由父组件传入)
- `modelValue: ManifestDraft`（双向绑定）

**Types**（声明在文件内或新 types 文件，自行决定）：

```ts
interface ManifestDraft {
  version: string;
  cases: ManifestDraftCase[];
}
interface ManifestDraftCase {
  id: string;          // 前端唯一 key，用于 list rendering
  name: string;
  input: string;       // zip 内路径
  output: string;
  sample: boolean;
  score: number | null;
}
```

**Behavior**:
- 接收 `entries` 后，自动按以下规则推断 cases（在 onMounted 或 watch
  immediate 里运行一次推断）：

  规则（按优先级）：
  1. 找成对 `*.in` ↔ `*.out` 同 basename → 一对 case
  2. 找成对 `*.txt`（input/ 子目录 ↔ output/ 子目录，同名）→ 一对 case
  3. 找成对 `cases/<n>/input.txt` ↔ `cases/<n>/output.txt`
  4. 兜底：把所有非目录文件按字母序两两配对（input + output）

  推断后填入 `modelValue.cases`。第一对 case 默认 `sample: true`，其余
  `false`。`name` 默认取 input 文件名去后缀。

- UI 展示：
  - 一个 `<a-input>` 编辑 `version`（默认值 `"v1-" + Date.now()`）
  - 一个表格 / 卡片列表，每行：
    - case name 输入框
    - input 路径下拉（选项是 `entries` 里所有 file path）
    - output 路径下拉
    - sample checkbox
    - score 输入框（可空）
    - 删除按钮
  - 一个「添加 case」按钮
  - 顶部提示框显示推断结果（"自动识别 N 对用例，可调整"）

- emit `update:modelValue` 在任何字段变化时

i18n keys (新增到 `packages/i18n/src/messages.ts` 的 `testcase` 段，
zh-CN 和 en-US 同步)：

| key | zh-CN | en-US |
|---|---|---|
| `manifestTitle` | 测试包清单（manifest） | Testcase manifest |
| `manifestHint` | 自动从压缩包识别用例，可手动调整。提交时会自动写入 manifest.json。 | Auto-detected from the zip — adjust if needed. A manifest.json will be written into the package on upload. |
| `manifestVersionLabel` | 版本号 | Version |
| `manifestVersionPlaceholder` | 例如 v1-2026-05-26 | e.g. v1-2026-05-26 |
| `manifestCaseName` | 用例名 | Case name |
| `manifestCaseInput` | 输入文件 | Input file |
| `manifestCaseOutput` | 期望输出文件 | Expected output file |
| `manifestCaseSample` | 样例 | Sample |
| `manifestCaseScore` | 分值 | Score |
| `manifestAddCase` | 添加用例 | Add case |
| `manifestRemoveCase` | 删除 | Remove |
| `manifestDetectedCount` | 已识别 {n} 对用例 | Detected {n} case(s) |
| `manifestEmpty` | 未识别到成对的输入/输出文件，请手动添加。 | No paired files found. Add cases manually. |
| `manifestInvalidPath` | 选定的文件不在压缩包内 | Selected file is not in the zip |
| `manifestAlreadyExists` | 压缩包内已包含 manifest.json，将直接使用该清单上传。 | The zip already contains manifest.json — it will be uploaded as-is. |
| `manifestRebuilding` | 正在为压缩包注入 manifest.json… | Injecting manifest.json into the zip… |

============================================================
TASK 3 — Wire the manifest editor into TestcasePackageUploader
============================================================

In `apps/web-admin/src/components/TestcasePackageUploader.vue`:

(A) 选文件后立即解析 zip 文件列表 — 新增 step:

```ts
import JSZip from 'jszip';
// ...
const zipEntries = ref<{ path: string; sizeBytes: number; isDir: boolean }[]>([]);
const hasExistingManifest = ref(false);
const manifestDraft = ref<ManifestDraft>({ version: '', cases: [] });

async function analyzeZip(file: File) {
  const zip = await JSZip.loadAsync(file);
  const entries: { path: string; sizeBytes: number; isDir: boolean }[] = [];
  let foundManifest = false;
  zip.forEach((path, entry) => {
    if (path === 'manifest.json' && !entry.dir) foundManifest = true;
    entries.push({
      path,
      sizeBytes: (entry as any)._data?.uncompressedSize ?? 0,
      isDir: entry.dir
    });
  });
  zipEntries.value = entries;
  hasExistingManifest.value = foundManifest;
}
```

在 `selectFile()` 的成功路径（zip 通过 onlyZip / fileTooLarge 校验后）
**追加** `await analyzeZip(file)`。

(B) Template 新增：选完文件后 if `!hasExistingManifest` 显示
`<TestcaseManifestEditor :entries="zipEntries" v-model="manifestDraft" />`；
if `hasExistingManifest` 显示 alert "压缩包内已包含 manifest.json，将
直接使用该清单上传。"

(C) `uploadSelected()` 改造：

```ts
async function uploadSelected() {
  // existing problemId / selectedFile checks ...

  busy.value = true;
  error.value = '';
  uploadStatus.value = null;

  try {
    let fileToUpload: Blob = selectedFile.value;
    if (!hasExistingManifest.value) {
      phase.value = 'building';  // new phase
      fileToUpload = await injectManifest(selectedFile.value, manifestDraft.value);
    }

    phase.value = 'hashing';
    fileSha256.value = await sha256(fileToUpload);

    // continue: init + chunk upload + complete (using fileToUpload instead of selectedFile)
    // ...
  } catch (caught) {
    phase.value = 'failed';
    canRetry.value = true;
    error.value = userErrorMessage(caught, t('testcase.initFailed'));
  } finally {
    busy.value = false;
  }
}
```

(D) `injectManifest` helper：

```ts
async function injectManifest(file: File, draft: ManifestDraft): Promise<Blob> {
  const zip = await JSZip.loadAsync(file);
  const payload = {
    version: draft.version,
    cases: draft.cases.map(c => ({
      name: c.name,
      input: c.input,
      output: c.output,
      sample: c.sample,
      score: c.score ?? undefined
    }))
  };
  zip.file('manifest.json', JSON.stringify(payload, null, 2));
  return zip.generateAsync({ type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 6 } });
}
```

(E) `chunkSizeBytes` / `totalChunks` 等基于新 `fileToUpload.size` 而
不是 `selectedFile.value.size`。把所有引用 `selectedFile.value` 用于
分块的地方改用一个本地 `fileToUpload` 变量（保持原 selectedFile 仍是
用户选的源文件，仅 UI 展示用）。

(F) 新增 `phase: 'building'` 在 `phaseText` 中显示
`t('testcase.manifestRebuilding')`。

(G) 在前端校验 manifestDraft：上传前如果 `draft.cases.length === 0`
或 `draft.version.trim() === ''`，设置 `error.value = t('testcase.manifestInvalid')`
并 return 不上传。（添加对应 i18n key：`manifestInvalid: '请至少配置一条用例并填写版本号。' / 'Configure at least one case and a version before uploading.'`）

(H) Power-user fast path：如果 `hasExistingManifest`，跳过 (A)-(G) 的
draft 校验，直接用原 `selectedFile`（即 `fileToUpload = selectedFile`），
不需要 phase=building。

============================================================
TASK 4 — Responsive UI
============================================================

ManifestEditor UI 必须遵循新 §5.8 响应式约束（如该约定已被前一个
inbox 加入 CLAUDE.md，对照执行；如果还没有，临时按以下原则落实）：

- desktop (≥1280px)：case 列表用表格布局
- tablet (768-1280px)：表格 + 横向 overflow auto
- mobile (<768px)：每个 case 作为卡片，字段堆栈

styles 在 `apps/web-admin/src/styles.css` 末尾追加 `.testcase-manifest-*`
相关样式段，按响应式断点写。

============================================================
TASK 5 — typecheck + build smoke
============================================================

- `npm run typecheck -w @aioj/web-admin` MUST exit 0
- Build smoke: `npm run build -w @aioj/web-admin` 应该通过（jszip 是
  纯 JS，无 build 烦恼，但要确认 tree-shaking 后 chunk 大小合理；
  报告里贴最后 ~10 行 build 输出）

============================================================
VERIFICATION
============================================================

1. `git status --short` — expected new modifications:
     M  apps/web-admin/package.json
     M  package-lock.json
     M  apps/web-admin/src/components/TestcasePackageUploader.vue
     M  apps/web-admin/src/styles.css
     M  packages/i18n/src/messages.ts
     M  docs/codex-exchange/inbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
     ?? apps/web-admin/src/components/TestcaseManifestEditor.vue
     ?? docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
   Baseline dirty files from prior tasks tolerated. NO extra source
   file modified outside this list.

2. Focused diffs for each modified file (~15 lines context).

3. Full content of the new `TestcaseManifestEditor.vue`.

4. typecheck output last 5 lines.

5. build output last 10 lines (with chunk size summary if vite shows
   it).

6. Manual verification checklist (for the human reviewer):
   [ ] Prepare a small test zip with files `cases/1.in`, `cases/1.out`,
       `cases/2.in`, `cases/2.out`. NO manifest.json inside.
   [ ] Open admin → 编辑题目 → 测试包 tab → 选择该 zip
   [ ] Manifest editor 出现，自动识别 2 对 case，version 自动填一个
       默认值，第一条 sample 被勾选
   [ ] 点上传 → 进度条显示 "正在为压缩包注入 manifest.json…" 一瞬
       → 然后 hashing → uploading → complete → READY
   [ ] 后端无 `Testcase package manifest.json is required` 报错
   [ ] 第二次试：准备一个 zip 里**已经放了 manifest.json**，选择
       后 editor 不出现，显示"压缩包内已包含 manifest.json"提示，
       直接走旧上传流程
   [ ] 错误流：故意把所有 case 的 input 选成不存在的路径，点上传
       → 后端拒绝时前端 alert 显示具体错误文本（不是空红框）
   [ ] 移动端断点 (Chrome devtools 360px)：manifest editor case 列表
       变成卡片式堆栈，没有横向滚动
   [ ] zh ↔ en 切换，所有新 i18n key 渲染正确

7. 6-sentence Chinese summary：覆盖：依赖、新组件位置、复用规则、
   power-user 路径如何保留、CLAUDE.md §5.8 响应式如何被遵循、
   后端无改动声明。

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md`
Then update inbox `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `apps/web-admin/package.json` (deps only)
- root `package-lock.json` (npm install side effect)
- `apps/web-admin/src/components/TestcasePackageUploader.vue`
- `apps/web-admin/src/components/TestcaseManifestEditor.vue` (new)
- `apps/web-admin/src/styles.css`
- `packages/i18n/src/messages.ts`
- This inbox (Status only)
- The outbox (new)

**Read for reference (do not modify)**:
- `backend/problem-service/src/main/java/.../TestcasePackageValidator.java`
  (manifest schema reference — already mirrored above)
- `packages/api-client/src/index.ts` (initTestcasePackage,
  uploadTestcaseChunk, completeTestcaseUpload signatures)

**Hard 禁区**:
- backend/**
- packages/api-client/**
- V*.sql migrations
- apps/web-user/**
- 其他 admin 组件
