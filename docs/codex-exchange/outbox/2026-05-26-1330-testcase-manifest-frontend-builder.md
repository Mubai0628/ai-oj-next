# Task: 测试包上传 UX 改造 —— 前端表单生成 manifest.json，自动注入 zip
## Status: done
## Created by: Codex @ 2026-05-26T20:40+08:00
## Linked: inbox/2026-05-26-1330-testcase-manifest-frontend-builder.md

---

## Report

### Section 1: git status --short

本任务相关文件：

```text
 M apps/web-admin/package.json
 M apps/web-admin/src/components/TestcasePackageUploader.vue
 M apps/web-admin/src/styles.css
 M package-lock.json
 M packages/i18n/src/messages.ts
?? apps/web-admin/src/components/TestcaseManifestEditor.vue
?? docs/codex-exchange/inbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
?? docs/codex-exchange/outbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
```

既有 baseline dirty files 保留，未回滚：

```text
 M CLAUDE.md
 M apps/web-admin/src/components/ProblemEditorDrawer.vue
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
 M docs/HANDOVER.md
 M docs/deployment.md
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? docs/codex-exchange/README.md
?? docs/codex-exchange/inbox/.gitkeep
?? docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/inbox/2026-05-26-1430-drawer-content-stretch-fix.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md
```

### Section 2: focused diffs

`apps/web-admin/package.json`

```diff
   "dependencies": {
     "@aioj/api-client": "0.1.0",
     "@aioj/i18n": "0.1.0",
-    "@aioj/ui": "0.1.0"
+    "@aioj/ui": "0.1.0",
+    "jszip": "^3.10.1"
   }
```

`package-lock.json`

```diff
       "dependencies": {
         "@aioj/api-client": "0.1.0",
         "@aioj/i18n": "0.1.0",
-        "@aioj/ui": "0.1.0"
+        "@aioj/ui": "0.1.0",
+        "jszip": "^3.10.1"
       }
...
+    "node_modules/jszip": {
+      "version": "3.10.1",
+      "dependencies": {
+        "lie": "~3.3.0",
+        "pako": "~1.0.2",
+        "readable-stream": "~2.3.6",
+        "setimmediate": "^1.0.5"
+      }
+    }
```

`apps/web-admin/src/components/TestcasePackageUploader.vue`

```diff
+    <a-alert v-if="selectedFile && hasExistingManifest" type="info" show-icon class="form-alert">
+      {{ t('testcase.manifestAlreadyExists') }}
+    </a-alert>
+
+    <TestcaseManifestEditor
+      v-if="selectedFile && !hasExistingManifest"
+      v-model="manifestDraft"
+      :entries="zipEntries"
+    />
...
+import JSZip from 'jszip';
+import TestcaseManifestEditor from '@/components/TestcaseManifestEditor.vue';
```

```diff
+const uploadSizeBytes = ref(0);
+const zipEntries = ref<ZipEntryInfo[]>([]);
+const hasExistingManifest = ref(false);
+const manifestDraft = ref<ManifestDraft>({ version: '', cases: [] });
-const phase = ref<'idle' | 'hashing' | 'uploading' | 'completing' | 'polling' | 'ready' | 'failed'>('idle');
+const phase = ref<'idle' | 'building' | 'hashing' | 'uploading' | 'completing' | 'polling' | 'ready' | 'failed'>('idle');
-const totalChunks = computed(() => selectedFile.value ? Math.ceil(selectedFile.value.size / chunkSizeBytes) : 0);
+const totalChunks = computed(() => uploadSizeBytes.value ? Math.ceil(uploadSizeBytes.value / chunkSizeBytes) : 0);
```

```diff
+async function analyzeZip(file: File) {
+  const zip = await JSZip.loadAsync(file);
+  const entries: ZipEntryInfo[] = [];
+  let foundManifest = false;
+
+  zip.forEach((path, entry) => {
+    const normalizedPath = path.replace(/\\/g, '/').replace(/^\.?\//, '');
+    if (normalizedPath === 'manifest.json' && !entry.dir) foundManifest = true;
+    const entryWithData = entry as typeof entry & { _data?: { uncompressedSize?: number } };
+    entries.push({ path: normalizedPath, sizeBytes: entryWithData._data?.uncompressedSize ?? 0, isDir: entry.dir });
+  });
+
+  zipEntries.value = entries;
+  hasExistingManifest.value = foundManifest;
+}
```

```diff
+async function injectManifest(file: File, draft: ManifestDraft): Promise<Blob> {
+  const zip = await JSZip.loadAsync(file);
+  const payload = {
+    version: draft.version.trim(),
+    cases: draft.cases.map((item) => ({
+      name: item.name.trim() || undefined,
+      input: item.input,
+      output: item.output,
+      sample: item.sample,
+      score: item.score ?? undefined
+    }))
+  };
+
+  zip.file('manifest.json', JSON.stringify(payload, null, 2));
+  return zip.generateAsync({ type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 6 } });
+}
```

```diff
+    let fileToUpload: Blob = selectedFile.value;
+
+    if (!hasExistingManifest.value) {
+      const validationError = validateManifestDraft(manifestDraft.value);
+      if (validationError) {
+        error.value = validationError;
+        phase.value = 'failed';
+        canRetry.value = true;
+        return;
+      }
+
+      phase.value = 'building';
+      fileToUpload = await injectManifest(selectedFile.value, manifestDraft.value);
+    }
+
+    uploadSizeBytes.value = fileToUpload.size;
+    phase.value = 'hashing';
+    fileSha256.value = await sha256(fileToUpload);
+    const totalChunksForUpload = Math.ceil(fileToUpload.size / chunkSizeBytes);
```

`apps/web-admin/src/styles.css`

```diff
+.testcase-manifest {
+  display: grid;
+  grid-template-columns: minmax(0, 1fr);
+  gap: 12px;
+  padding: 14px;
+  border: 1px solid #dbeafe;
+  border-radius: 12px;
+  background: #f8fbff;
+}
+
+.testcase-manifest-table-wrap {
+  width: 100%;
+  overflow-x: auto;
+}
+
+.testcase-manifest-table {
+  width: 100%;
+  min-width: 760px;
+  border-collapse: collapse;
+}
+
+.testcase-manifest-card-list {
+  display: none;
+}
+
+@media (max-width: 767px) {
+  .testcase-manifest-table-wrap { display: none; }
+  .testcase-manifest-card-list {
+    display: grid;
+    gap: 10px;
+  }
+}
```

`packages/i18n/src/messages.ts`

```diff
+      manifestTitle: '测试包清单（manifest）',
+      manifestHint: '自动从压缩包识别用例，可手动调整。提交时会自动写入 manifest.json。',
+      manifestVersionLabel: '版本号',
+      manifestVersionPlaceholder: '例如 v1-2026-05-26',
+      manifestCaseName: '用例名',
+      manifestCaseInput: '输入文件',
+      manifestCaseOutput: '期望输出文件',
+      manifestCaseSample: '样例',
+      manifestCaseScore: '分值',
+      manifestAddCase: '添加用例',
+      manifestRemoveCase: '删除',
+      manifestDetectedCount: '已识别 {n} 对用例',
+      manifestEmpty: '未识别到成对的输入/输出文件，请手动添加。',
+      manifestInvalidPath: '选定的文件不在压缩包内',
+      manifestAlreadyExists: '压缩包内已包含 manifest.json，将直接使用该清单上传。',
+      manifestRebuilding: '正在为压缩包注入 manifest.json…',
+      manifestInvalid: '请至少配置一条用例并填写版本号。'
```

```diff
+      manifestTitle: 'Testcase manifest',
+      manifestHint: 'Auto-detected from the zip — adjust if needed. A manifest.json will be written into the package on upload.',
+      manifestVersionLabel: 'Version',
+      manifestVersionPlaceholder: 'e.g. v1-2026-05-26',
+      manifestCaseName: 'Case name',
+      manifestCaseInput: 'Input file',
+      manifestCaseOutput: 'Expected output file',
+      manifestCaseSample: 'Sample',
+      manifestCaseScore: 'Score',
+      manifestAddCase: 'Add case',
+      manifestRemoveCase: 'Remove',
+      manifestDetectedCount: 'Detected {n} case(s)',
+      manifestEmpty: 'No paired files found. Add cases manually.',
+      manifestInvalidPath: 'Selected file is not in the zip',
+      manifestAlreadyExists: 'The zip already contains manifest.json — it will be uploaded as-is.',
+      manifestRebuilding: 'Injecting manifest.json into the zip…',
+      manifestInvalid: 'Configure at least one case and a version before uploading.'
```

### Section 3: full new component files

`apps/web-admin/src/components/TestcaseManifestEditor.vue`

```vue
<template>
  <section class="testcase-manifest">
    <a-alert type="info" show-icon class="testcase-manifest-alert">
      <strong>{{ t('testcase.manifestTitle') }}</strong>
      <span>{{ t('testcase.manifestHint') }}</span>
      <small>{{ detectedMessage }}</small>
    </a-alert>

    <a-form :model="draft" layout="vertical" class="testcase-manifest-form">
      <a-form-item :label="t('testcase.manifestVersionLabel')">
        <a-input v-model="draft.version" :placeholder="t('testcase.manifestVersionPlaceholder')" />
      </a-form-item>
    </a-form>

    <div class="testcase-manifest-head">
      <strong>{{ t('testcase.manifestDetectedCount', { n: draft.cases.length }) }}</strong>
      <a-button size="small" type="outline" @click="addCase">{{ t('testcase.manifestAddCase') }}</a-button>
    </div>

    <a-empty v-if="!draft.cases.length" :description="t('testcase.manifestEmpty')" />

    <div v-else class="testcase-manifest-table-wrap">
      <table class="testcase-manifest-table">
        <thead>
          <tr>
            <th>{{ t('testcase.manifestCaseName') }}</th>
            <th>{{ t('testcase.manifestCaseInput') }}</th>
            <th>{{ t('testcase.manifestCaseOutput') }}</th>
            <th>{{ t('testcase.manifestCaseSample') }}</th>
            <th>{{ t('testcase.manifestCaseScore') }}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in draft.cases" :key="item.id">
            <td><a-input v-model="item.name" size="small" /></td>
            <td>
              <a-select v-model="item.input" size="small" allow-search>
                <a-option v-for="path in filePaths" :key="path" :value="path">{{ path }}</a-option>
              </a-select>
            </td>
            <td>
              <a-select v-model="item.output" size="small" allow-search>
                <a-option v-for="path in filePaths" :key="path" :value="path">{{ path }}</a-option>
              </a-select>
            </td>
            <td><a-checkbox v-model="item.sample" /></td>
            <td>
              <a-input-number
                :model-value="item.score ?? undefined"
                size="small"
                :min="0"
                :precision="0"
                hide-button
                @update:model-value="(value) => item.score = typeof value === 'number' ? value : null"
              />
            </td>
            <td>
              <a-button size="mini" status="danger" @click="removeCase(index)">
                {{ t('testcase.manifestRemoveCase') }}
              </a-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="draft.cases.length" class="testcase-manifest-card-list">
      <article v-for="(item, index) in draft.cases" :key="item.id" class="testcase-manifest-card">
        <a-form :model="item" layout="vertical">
          <a-form-item :label="t('testcase.manifestCaseName')">
            <a-input v-model="item.name" size="small" />
          </a-form-item>
          <a-form-item :label="t('testcase.manifestCaseInput')">
            <a-select v-model="item.input" size="small" allow-search>
              <a-option v-for="path in filePaths" :key="path" :value="path">{{ path }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('testcase.manifestCaseOutput')">
            <a-select v-model="item.output" size="small" allow-search>
              <a-option v-for="path in filePaths" :key="path" :value="path">{{ path }}</a-option>
            </a-select>
          </a-form-item>
          <div class="testcase-manifest-card-row">
            <a-checkbox v-model="item.sample">{{ t('testcase.manifestCaseSample') }}</a-checkbox>
            <a-input-number
              :model-value="item.score ?? undefined"
              size="small"
              :min="0"
              :precision="0"
              hide-button
              :placeholder="t('testcase.manifestCaseScore')"
              @update:model-value="(value) => item.score = typeof value === 'number' ? value : null"
            />
          </div>
          <a-button size="small" status="danger" @click="removeCase(index)">
            {{ t('testcase.manifestRemoveCase') }}
          </a-button>
        </a-form>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

interface ZipEntryInfo {
  path: string;
  sizeBytes: number;
  isDir: boolean;
}

interface ManifestDraftCase {
  id: string;
  name: string;
  input: string;
  output: string;
  sample: boolean;
  score: number | null;
}

interface ManifestDraft {
  version: string;
  cases: ManifestDraftCase[];
}

const props = defineProps<{
  entries: ZipEntryInfo[];
  modelValue: ManifestDraft;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: ManifestDraft] }>();

const { t } = useI18n();
const draft = ref<ManifestDraft>(cloneDraft(props.modelValue));

const filePaths = computed(() =>
  props.entries
    .filter((entry) => !entry.isDir && normalizePath(entry.path) !== 'manifest.json')
    .map((entry) => normalizePath(entry.path))
    .sort((a, b) => a.localeCompare(b))
);

const detectedMessage = computed(() =>
  draft.value.cases.length
    ? t('testcase.manifestDetectedCount', { n: draft.value.cases.length })
    : t('testcase.manifestEmpty')
);

const entriesSignature = computed(() =>
  props.entries.map((entry) => `${entry.path}:${entry.sizeBytes}:${entry.isDir ? 1 : 0}`).join('|')
);

watch(
  () => props.modelValue,
  (value) => {
    if (JSON.stringify(value) !== JSON.stringify(draft.value)) {
      draft.value = cloneDraft(value);
    }
  },
  { deep: true }
);

watch(
  draft,
  (value) => {
    emit('update:modelValue', cloneDraft(value));
  },
  { deep: true }
);

watch(
  entriesSignature,
  () => {
    if (!props.entries.length) return;
    draft.value = inferDraft();
  },
  { immediate: true }
);

function cloneDraft(value: ManifestDraft): ManifestDraft {
  return {
    version: value.version || '',
    cases: (value.cases || []).map((item) => ({ ...item }))
  };
}

function normalizePath(value: string) {
  return value.replace(/\\/g, '/').replace(/^\.?\//, '');
}

function fileNameWithoutExt(path: string) {
  const fileName = normalizePath(path).split('/').pop() || path;
  return fileName.replace(/\.[^.]+$/, '');
}

function buildCase(input: string, output: string, index: number): ManifestDraftCase {
  return {
    id: `${index}-${input}-${output}`,
    name: fileNameWithoutExt(input) || `case-${index + 1}`,
    input,
    output,
    sample: index === 0,
    score: null
  };
}

function inferDraft(): ManifestDraft {
  const pairs = inferPairs();
  return {
    version: `v1-${Date.now()}`,
    cases: pairs.map(([input, output], index) => buildCase(input, output, index))
  };
}

function inferPairs(): Array<[string, string]> {
  const files = filePaths.value;
  return (
    inferDotInOutPairs(files) ||
    inferInputOutputFolderPairs(files) ||
    inferCasesFolderPairs(files) ||
    inferFallbackPairs(files)
  );
}

function inferDotInOutPairs(files: string[]) {
  const outputs = new Map<string, string>();
  files
    .filter((path) => /\.out$/i.test(path))
    .forEach((path) => outputs.set(path.replace(/\.out$/i, ''), path));

  const pairs = files
    .filter((path) => /\.in$/i.test(path))
    .map((input) => [input, outputs.get(input.replace(/\.in$/i, ''))] as const)
    .filter((pair): pair is readonly [string, string] => Boolean(pair[1]))
    .map(([input, output]) => [input, output] as [string, string]);

  return pairs.length ? pairs : null;
}

function inferInputOutputFolderPairs(files: string[]) {
  const outputs = new Map<string, string>();
  files.forEach((path) => {
    const match = path.match(/(?:^|\/)output\/(.+\.txt)$/i);
    if (match) outputs.set(match[1], path);
  });

  const pairs = files
    .map((input) => {
      const match = input.match(/(?:^|\/)input\/(.+\.txt)$/i);
      return match ? [input, outputs.get(match[1])] as const : null;
    })
    .filter((pair): pair is readonly [string, string] => Boolean(pair?.[1]))
    .map(([input, output]) => [input, output] as [string, string]);

  return pairs.length ? pairs : null;
}

function inferCasesFolderPairs(files: string[]) {
  const outputs = new Map<string, string>();
  files.forEach((path) => {
    const match = path.match(/(?:^|\/)cases\/([^/]+)\/output\.txt$/i);
    if (match) outputs.set(match[1], path);
  });

  const pairs = files
    .map((input) => {
      const match = input.match(/(?:^|\/)cases\/([^/]+)\/input\.txt$/i);
      return match ? [input, outputs.get(match[1])] as const : null;
    })
    .filter((pair): pair is readonly [string, string] => Boolean(pair?.[1]))
    .map(([input, output]) => [input, output] as [string, string]);

  return pairs.length ? pairs : null;
}

function inferFallbackPairs(files: string[]) {
  const pairs: Array<[string, string]> = [];
  for (let index = 0; index + 1 < files.length; index += 2) {
    pairs.push([files[index], files[index + 1]]);
  }
  return pairs;
}

function addCase() {
  const paths = filePaths.value;
  draft.value.cases.push({
    id: `manual-${Date.now()}-${draft.value.cases.length}`,
    name: `case-${draft.value.cases.length + 1}`,
    input: paths[0] || '',
    output: paths[1] || paths[0] || '',
    sample: !draft.value.cases.length,
    score: null
  });
}

function removeCase(index: number) {
  draft.value.cases.splice(index, 1);
  if (draft.value.cases.length && !draft.value.cases.some((item) => item.sample)) {
    draft.value.cases[0].sample = true;
  }
}
</script>
```

### Section 4: typecheck last 5 lines

```text
Active code page: 65001

> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 5: build last 10 lines

```text
dist/assets/style-1lwF6q0M.js          830.05 kB │ gzip: 287.84 kB

✓ built in 1.21s
[plugin builtin:vite-reporter]
(!) Some chunks are larger than 500 kB after minification. Consider:
- Using dynamic import() to code-split the application
- Use build.rolldownOptions.output.codeSplitting to improve chunking: https://rolldown.rs/reference/OutputOptions.codeSplitting
- Adjust chunk size limit for this warning via build.chunkSizeWarningLimit.
```

### Section 6: manual verification checklist

[ ] Prepare a small test zip with files `cases/1.in`, `cases/1.out`, `cases/2.in`, `cases/2.out`. NO manifest.json inside.
[ ] Open admin → 编辑题目 → 测试包 tab → 选择该 zip
[ ] Manifest editor 出现，自动识别 2 对 case，version 自动填一个默认值，第一条 sample 被勾选
[ ] 点上传 → 进度条显示 "正在为压缩包注入 manifest.json…" 一瞬 → 然后 hashing → uploading → complete → READY
[ ] 后端无 `Testcase package manifest.json is required` 报错
[ ] 第二次试：准备一个 zip 里**已经放了 manifest.json**，选择后 editor 不出现，显示"压缩包内已包含 manifest.json"提示，直接走旧上传流程
[ ] 错误流：故意把所有 case 的 input 选成不存在的路径，点上传 → 后端拒绝时前端 alert 显示具体错误文本（不是空红框）
[ ] 移动端断点 (Chrome devtools 360px)：manifest editor case 列表变成卡片式堆栈，没有横向滚动
[ ] zh ↔ en 切换，所有新 i18n key 渲染正确

### Section 7: Chinese summary

本次在管理端新增了 `jszip` 依赖，并确认 `package-lock.json` 只新增 jszip 及其依赖子图。新增的 `TestcaseManifestEditor.vue` 位于 `apps/web-admin/src/components/`，负责从 zip 文件列表中按 `.in/.out`、`input/output`、`cases/<n>/input.txt/output.txt` 和兜底两两配对规则推断测试用例。`TestcasePackageUploader.vue` 现在会在用户选择 zip 后先解析文件列表，没有 `manifest.json` 时展示可编辑清单，上传前把生成的 `manifest.json` 注入 zip 并重新计算 sha256、分块数量和 chunk hash。Power-user 路径保持不变：zip 根目录已有 `manifest.json` 时跳过编辑器和重打包，直接按旧流程上传原始 zip。Manifest 编辑区遵守 CLAUDE.md §5.8 的响应式约束：桌面/平板用表格并允许横向滚动，移动端切成卡片堆栈，避免把抽屉内容撑乱。后端、api-client、测试包 validator 和迁移文件都没有改动。

## Next-action hint

- 建议人工用一个无 manifest 的小 zip 和一个已有 manifest 的 zip 各跑一次上传闭环，确认后端处理状态从 PROCESSING 进入 READY。
- 如果未来要继续提升体验，可以在清单编辑器里增加“下载将要写入的 manifest.json”预览按钮，便于教师复核。
