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
            <td>
              <a-input v-model="item.name" size="small" />
            </td>
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
            <td>
              <a-checkbox v-model="item.sample" />
            </td>
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
