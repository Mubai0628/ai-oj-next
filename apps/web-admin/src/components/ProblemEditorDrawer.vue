<template>
  <a-drawer
    v-model:visible="visibleProxy"
    :width="drawerWidth"
    :footer="false"
    class="problem-editor-drawer"
    unmount-on-close
  >
    <template #title>
      <div class="problem-editor-header">
        <span class="problem-editor-eyebrow">{{ t('common.adminConsole') }}</span>
        <strong>{{ editingId ? t('problems.editModal') : t('problems.createModal') }}</strong>
        <small>{{ t('problems.drawerSubtitle') }}</small>
      </div>
    </template>

    <a-spin :loading="loadingDetail">
      <a-form :model="form" layout="vertical" class="problem-editor">
        <a-radio-group v-model="activeTab" type="button" class="problem-editor-tabs">
          <a-radio value="basic">{{ t('problems.editorBasic') }}</a-radio>
          <a-radio value="statement">{{ t('problems.editorStatement') }}</a-radio>
          <a-radio value="package">{{ t('problems.editorPackage') }}</a-radio>
        </a-radio-group>
        <a-alert v-if="orphanedFieldErrors.length" type="error" show-icon class="form-alert">
          {{ orphanedFieldErrors.join('；') }}
        </a-alert>

        <section v-if="activeTab === 'basic'" class="problem-editor-section">
          <p class="problem-editor-inline-tip">{{ t('problems.basicInfoTip') }}</p>

          <div class="basic-info-layout">
            <article class="basic-form-card">
              <div class="basic-form-grid">
                <a-form-item
                  class="basic-form-field basic-form-field--wide"
                  :label="requiredLabel(t('problems.titleLabel'))"
                  :validate-status="fieldError('title') ? 'error' : undefined"
                  :help="fieldError('title') || undefined"
                >
                  <a-input v-model="form.title" :max-length="100" :placeholder="t('problems.titlePlaceholder')" />
                  <template #extra>{{ t('problems.charCount', { count: form.title.length, max: 100 }) }}</template>
                </a-form-item>

                <a-form-item :label="requiredLabel(t('common.difficulty'))">
                  <a-select v-model="form.difficulty">
                    <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
                      <span :class="difficultyClass(difficulty)" /> {{ difficultyLabel(difficulty) }}
                    </a-option>
                  </a-select>
                </a-form-item>

                <a-form-item :label="t('common.tags')" class="problem-editor-tags-field">
                  <a-input v-model="tagText" :placeholder="t('problems.tagsInputPlaceholder')" @press-enter="tagText = parseTags().join(', ')" />
                  <template #extra>
                    <div v-if="tagPreview.length" class="tag-preview">
                      <a-tag v-for="tag in tagPreview" :key="tag" color="arcoblue">{{ tag }}</a-tag>
                    </div>
                    <span v-else>{{ t('problems.tagsHelper') }}</span>
                  </template>
                </a-form-item>

                <a-form-item
                  :label="requiredLabel(t('problems.timeLimit'))"
                  :validate-status="fieldError('timeLimitMillis') ? 'error' : undefined"
                  :help="fieldError('timeLimitMillis') || undefined"
                >
                  <a-input-number v-model="form.timeLimitMillis" :min="100" :step="100" hide-button>
                    <template #suffix>ms</template>
                  </a-input-number>
                  <template #extra>{{ t('problems.timeLimitHelper') }}</template>
                </a-form-item>

                <a-form-item
                  :label="requiredLabel(t('problems.memoryLimit'))"
                  :validate-status="fieldError('memoryLimitKb') ? 'error' : undefined"
                  :help="fieldError('memoryLimitKb') || undefined"
                >
                  <a-input-number v-model="memoryLimitMb" :min="16" :step="16" hide-button>
                    <template #suffix>MB</template>
                  </a-input-number>
                  <template #extra>{{ t('problems.memoryLimitHelper') }}</template>
                </a-form-item>
              </div>
            </article>

            <aside class="basic-side-panel">
              <article class="basic-preview-card">
                <h3>{{ t('problems.basicPreview') }}</h3>
                <dl class="basic-preview-list">
                  <div>
                    <dt>{{ t('problems.previewDifficulty') }}</dt>
                    <dd><span :class="difficultyClass(form.difficulty)" /> {{ difficultyLabel(form.difficulty) }}</dd>
                  </div>
                  <div>
                    <dt>{{ t('problems.previewTime') }}</dt>
                    <dd>{{ form.timeLimitMillis }} ms</dd>
                  </div>
                  <div>
                    <dt>{{ t('problems.previewMemory') }}</dt>
                    <dd>{{ memoryLimitMb }} MB</dd>
                  </div>
                  <div>
                    <dt>{{ t('problems.previewTags') }}</dt>
                    <dd>
                      <div v-if="tagPreview.length" class="problem-editor-tags">
                        <a-tag v-for="tag in tagPreview" :key="tag" color="arcoblue">{{ tag }}</a-tag>
                      </div>
                      <span v-else class="no-tags">{{ t('problems.noTags') }}</span>
                    </dd>
                  </div>
                </dl>
              </article>

              <article class="problem-editor-tip-card">
                <span>💡</span>
                <strong>{{ t('problems.tipTitle') }}</strong>
                <p>{{ t('problems.tipCopy') }}</p>
              </article>
            </aside>
          </div>
        </section>

        <section v-else-if="activeTab === 'statement'" class="problem-editor-section problem-editor-stack">
          <article class="statement-editor-card">
            <div class="statement-editor-head">
              <div>
                <h2>{{ t('problems.statementEditor') }}</h2>
                <p>{{ t('problems.statementEditorCopy') }}</p>
              </div>
            </div>
            <a-form-item
              class="editor-form-item"
              :validate-status="fieldError('statement') ? 'error' : undefined"
              :help="fieldError('statement') || undefined"
            >
              <MdEditor
                v-model="form.statement"
                language="zh-CN"
                :max-length="20000"
                preview-theme="github"
                code-theme="github"
                :toolbars="statementToolbars"
                class="problem-md-editor"
              />
            </a-form-item>
            <div class="statement-helper">{{ t('problems.statementHelper') }}</div>
          </article>

          <article class="statement-editor-card">
            <div class="statement-editor-head">
              <div>
                <h2>{{ t('problems.notesLabel') }}</h2>
                <p>{{ t('problems.notesHelper') }}</p>
              </div>
            </div>
            <a-form-item
              class="editor-form-item"
              :validate-status="fieldError('notes') ? 'error' : undefined"
              :help="fieldError('notes') || undefined"
            >
              <MdEditor
                v-model="form.notes"
                language="zh-CN"
                :max-length="20000"
                preview-theme="github"
                code-theme="github"
                :placeholder="t('problems.notesEditorPlaceholder')"
                :toolbars="notesToolbars"
                class="problem-md-editor"
              />
            </a-form-item>
          </article>

          <article class="sample-section">
            <div class="section-title">
              <div>
                <h2>{{ t('problems.testCases') }}</h2>
                <p>{{ t('problems.sampleSectionCopy') }}</p>
              </div>
              <a-button type="primary" size="small" @click="addCase">{{ t('problems.addCase') }}</a-button>
            </div>

            <div class="sample-list">
              <section v-for="(testCase, index) in form.testCases" :key="index" class="sample-card">
                <div class="sample-card-header">
                  <div class="sample-title">
                    <span class="drag-handle">⋮⋮</span>
                    <strong>{{ t('problems.caseTitle', { index: index + 1 }) }}</strong>
                  </div>
                  <a-space>
                    <a-checkbox v-model="testCase.sample">{{ t('problems.sample') }}</a-checkbox>
                    <a-button size="mini" status="danger" :disabled="form.testCases.length === 1" @click="removeCase(index)">
                      {{ t('common.remove') }}
                    </a-button>
                  </a-space>
                </div>
                <div class="sample-card-body">
                  <div class="sample-stack">
                    <a-form-item
                      :label="t('problems.input')"
                      class="sample-field"
                      :validate-status="fieldError(`testCases[${index}].input`) ? 'error' : undefined"
                      :help="fieldError(`testCases[${index}].input`) || undefined"
                    >
                      <a-textarea v-model="testCase.input" class="sample-textarea" :auto-size="{ minRows: 3, maxRows: 12 }" />
                    </a-form-item>
                    <a-form-item
                      :label="t('problems.expectedOutput')"
                      class="sample-field"
                      :validate-status="fieldError(`testCases[${index}].expectedOutput`) ? 'error' : undefined"
                      :help="fieldError(`testCases[${index}].expectedOutput`) || undefined"
                    >
                      <a-textarea v-model="testCase.expectedOutput" class="sample-textarea" :auto-size="{ minRows: 3, maxRows: 12 }" />
                    </a-form-item>
                  </div>
                </div>
              </section>
            </div>

            <a-alert type="info" show-icon class="problem-editor-alert">
              {{ t('problems.sampleHelp') }}
            </a-alert>
          </article>
        </section>

        <section v-else class="problem-editor-section">
          <template v-if="!editingId">
            <article class="testcase-unsaved-card">
              <span>☁</span>
              <div>
                <strong>{{ t('problems.unsavedPackageTitle') }}</strong>
                <p>{{ t('problems.unsavedPackageCopy') }}</p>
              </div>
            </article>

            <div class="testcase-steps">
              <article v-for="(step, index) in testcaseSteps" :key="step.title" class="testcase-step-card">
                <span>{{ index + 1 }}</span>
                <strong>{{ step.title }}</strong>
                <p>{{ step.copy }}</p>
              </article>
            </div>

            <article class="testcase-upload-placeholder">
              <div class="upload-cloud">☁</div>
              <strong>{{ t('problems.uploadAfterSave') }}</strong>
              <p>{{ t('problems.uploadAfterSaveCopy') }}</p>
              <a-button disabled>{{ t('testcase.selectZip') }}</a-button>
            </article>

            <div class="testcase-empty-grid">
              <article class="testcase-empty-card">
                <strong>{{ t('testcase.activePackage') }}</strong>
                <p>{{ t('problems.noActivePackageCopy') }}</p>
              </article>
              <article class="testcase-empty-card">
                <strong>{{ t('testcase.packages') }}</strong>
                <p>{{ t('problems.noPackageHistoryCopy') }}</p>
              </article>
            </div>
          </template>

          <template v-else>
            <article class="testcase-saved-card">
              <span>{{ t('common.id') }}</span>
              <strong>#{{ editingId }}</strong>
              <p>{{ t('problems.savedPackageCopy') }}</p>
            </article>
            <TestcasePackageUploader :problem-id="editingId" />
          </template>
        </section>
      </a-form>
    </a-spin>

    <div class="problem-editor-footer">
      <a-button :disabled="saving" @click="visibleProxy = false">{{ t('common.cancel') }}</a-button>
      <a-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { MdEditor } from 'md-editor-v3';
import type { ToolbarNames } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ApiError, api, type Difficulty, type EntityId, type ProblemPayload, type ProblemResponse, type TestCaseDto } from '@aioj/api-client';
import TestcasePackageUploader from '@/components/TestcasePackageUploader.vue';

const props = defineProps<{
  visible: boolean;
  problem?: ProblemResponse | null;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  saved: [];
}>();

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const activeTab = ref<'basic' | 'statement' | 'package'>('basic');
const editingId = ref<EntityId | null>(null);
const tagText = ref('');
const saving = ref(false);
const loadingDetail = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const form = reactive<ProblemPayload>({
  title: '',
  difficulty: 'EASY',
  statement: '',
  notes: '',
  tags: [],
  testCases: [emptyCase(true)],
  timeLimitMillis: 1000,
  memoryLimitKb: 262144
});

const statementToolbars: ToolbarNames[] = [
  'bold', 'underline', 'italic', '-',
  'title', 'strikeThrough', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task', '-',
  'codeRow', 'code', 'link', 'image', 'table', 'mermaid', 'katex', '-',
  'revoke', 'next', '-', 'preview', 'previewOnly', 'fullscreen'
];
const notesToolbars: ToolbarNames[] = statementToolbars;

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
});

const drawerWidth = 'clamp(720px, 45vw, 920px)';

const memoryLimitMb = computed({
  get: () => form.memoryLimitKb / 1024,
  set: (value: number) => {
    const mb = Number(value);
    if (!Number.isFinite(mb) || mb <= 0) return;
    form.memoryLimitKb = Math.round(mb * 1024);
  }
});

const tagPreview = computed(() => parseTags());

const knownFieldPaths = computed(() => {
  const paths = new Set(['title', 'statement', 'notes', 'timeLimitMillis', 'memoryLimitKb']);
  form.testCases.forEach((_, index) => {
    paths.add(`testCases[${index}].input`);
    paths.add(`testCases[${index}].expectedOutput`);
  });
  return paths;
});

const orphanedFieldErrors = computed(() =>
  Object.entries(fieldErrors.value)
    .filter(([path]) => !knownFieldPaths.value.has(path))
    .map(([path, message]) => `${path}: ${message}`)
);

const testcaseSteps = computed(() => [
  { title: t('problems.packageStepSave'), copy: t('problems.packageStepSaveCopy') },
  { title: t('problems.packageStepUpload'), copy: t('problems.packageStepUploadCopy') },
  { title: t('problems.packageStepActivate'), copy: t('problems.packageStepActivateCopy') }
]);

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      void initialize();
    }
  }
);

function emptyCase(sample = false): TestCaseDto {
  return { input: '', expectedOutput: '', sample };
}

function requiredLabel(label: string) {
  return `${label} *`;
}

function difficultyLabel(difficulty: Difficulty) {
  return t(`difficulty.${difficulty}`);
}

function difficultyClass(difficulty: Difficulty) {
  return `difficulty-dot difficulty-dot--${difficulty.toLowerCase()}`;
}

function fieldError(path: string) {
  return fieldErrors.value[path];
}

function parseTags() {
  return Array.from(new Set(tagText.value.split(',').map((tag) => tag.trim()).filter(Boolean)));
}

function resetForm() {
  editingId.value = null;
  activeTab.value = 'basic';
  form.title = '';
  form.difficulty = 'EASY';
  form.statement = '';
  form.notes = '';
  form.tags = [];
  form.testCases = [emptyCase(true)];
  form.timeLimitMillis = 1000;
  form.memoryLimitKb = 262144;
  tagText.value = '';
  fieldErrors.value = {};
}

async function initialize() {
  resetForm();
  const selected = props.problem;
  if (!selected?.id) return;
  loadingDetail.value = true;
  try {
    const detail = await api.problem(selected.id);
    editingId.value = detail.id;
    form.title = detail.title;
    form.difficulty = detail.difficulty;
    form.statement = detail.statement;
    form.notes = detail.notes || '';
    form.tags = [...detail.tags];
    form.testCases = detail.samples.length ? detail.samples.map((item) => ({ ...item })) : [emptyCase(true)];
    form.timeLimitMillis = detail.timeLimitMillis;
    form.memoryLimitKb = detail.memoryLimitKb;
    tagText.value = detail.tags.join(', ');
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('problems.loadOneFailed'));
    visibleProxy.value = false;
  } finally {
    loadingDetail.value = false;
  }
}

function addCase() {
  form.testCases.push(emptyCase(false));
}

function removeCase(index: number) {
  if (form.testCases.length === 1) return;
  form.testCases.splice(index, 1);
  if (!form.testCases.some((item) => item.sample)) {
    form.testCases[0].sample = true;
  }
}

function payload(): ProblemPayload {
  const testCases = form.testCases.map((testCase) => ({
    input: testCase.input,
    expectedOutput: testCase.expectedOutput,
    sample: testCase.sample
  }));
  return {
    title: form.title.trim(),
    difficulty: form.difficulty,
    statement: form.statement.trim(),
    notes: (form.notes || '').trim() || undefined,
    tags: parseTags(),
    testCases,
    timeLimitMillis: Number(form.timeLimitMillis ?? 1000),
    memoryLimitKb: Number(form.memoryLimitKb ?? 262144)
  };
}

function validatePayload(problemPayload: ProblemPayload) {
  if (!problemPayload.title) return t('problems.titleRequired');
  if (problemPayload.title.length > 100) return t('problems.titleTooLong');
  if (!problemPayload.statement) return t('problems.statementRequired');
  if (problemPayload.statement.length > 20000) return t('problems.statementTooLong');
  if (problemPayload.timeLimitMillis < 100) return t('problems.timeLimitInvalid');
  if (problemPayload.memoryLimitKb < 16 * 1024) return t('problems.memoryLimitInvalid');
  if (!problemPayload.testCases.length || !problemPayload.testCases.some((testCase) => testCase.sample)) return t('problems.sampleRequired');
  return '';
}

async function save() {
  fieldErrors.value = {};
  const problemPayload = payload();
  const validationError = validatePayload(problemPayload);
  if (validationError) {
    Message.warning(validationError);
    return;
  }
  if (problemPayload.testCases.some((testCase) => !testCase.input.trim() || !testCase.expectedOutput.trim())) {
    Message.warning(t('problems.emptySampleWarning'));
    return;
  }

  saving.value = true;
  try {
    if (editingId.value) {
      await api.updateProblem(editingId.value, problemPayload);
      Message.success(t('problems.updated'));
    } else {
      await api.createProblem(problemPayload);
      Message.success(t('problems.created'));
    }
    fieldErrors.value = {};
    visibleProxy.value = false;
    emit('saved');
  } catch (caught) {
    if (caught instanceof ApiError) {
      fieldErrors.value = caught.details ?? {};
      Message.error(caught.userMessage);
    } else {
      Message.error(caught instanceof Error ? caught.message : t('problems.saveFailed'));
    }
  } finally {
    saving.value = false;
  }
}
</script>
