<template>
  <a-drawer
    v-model:visible="visibleProxy"
    :width="'clamp(860px, 62vw, 1080px)'"
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
        <a-tabs v-model:active-key="activeTab" type="rounded" class="problem-editor-tabs">
          <a-tab-pane key="basic" :title="t('problems.editorBasic')" />
          <a-tab-pane key="statement" :title="t('problems.editorStatement')" />
          <a-tab-pane key="package" :title="t('problems.editorPackage')" />
        </a-tabs>

        <section v-if="activeTab === 'basic'" class="problem-editor-section">
          <a-alert type="info" show-icon class="problem-editor-alert" :content="t('problems.basicInfoTip')" />

          <div class="problem-editor-grid">
            <div class="problem-editor-form-card">
              <div class="problem-editor-grid two">
                <a-form-item :label="requiredLabel(t('problems.titleLabel'))">
                  <a-input v-model="form.title" :max-length="100" :placeholder="t('problems.titlePlaceholder')" />
                  <template #extra>{{ t('problems.charCount', { count: form.title.length, max: 100 }) }}</template>
                </a-form-item>

                <a-form-item :label="requiredLabel(t('common.difficulty'))">
                  <a-select v-model="form.difficulty">
                    <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
                      {{ difficultyIcon(difficulty) }} {{ difficultyLabel(difficulty) }}
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

                <div class="problem-editor-grid two compact">
                  <a-form-item :label="requiredLabel(t('problems.timeLimit'))">
                    <a-input-number v-model="form.timeLimitMillis" :min="100" :step="100" hide-button>
                      <template #suffix>ms</template>
                    </a-input-number>
                    <template #extra>{{ t('problems.timeLimitHelper') }}</template>
                  </a-form-item>
                  <a-form-item :label="requiredLabel(t('problems.memoryLimit'))">
                    <a-input-number v-model="memoryLimitMb" :min="16" :step="16" hide-button>
                      <template #suffix>MB</template>
                    </a-input-number>
                    <template #extra>{{ t('problems.memoryLimitHelper') }}</template>
                  </a-form-item>
                </div>
              </div>
            </div>

            <aside class="problem-editor-tip-card">
              <span>💡</span>
              <strong>{{ t('problems.tipTitle') }}</strong>
              <p>{{ t('problems.tipCopy') }}</p>
            </aside>
          </div>
        </section>

        <section v-else-if="activeTab === 'statement'" class="problem-editor-section">
          <article class="statement-editor-card">
            <div class="statement-editor-head">
              <div>
                <h2>{{ t('problems.statementEditor') }}</h2>
                <p>{{ t('problems.statementEditorCopy') }}</p>
              </div>
              <a-button size="small" disabled>{{ t('problems.fullscreenEdit') }}</a-button>
            </div>
            <div class="statement-toolbar" aria-hidden="true">
              <button v-for="tool in statementTools" :key="tool" type="button" disabled>{{ tool }}</button>
            </div>
            <a-textarea v-model="form.statement" class="statement-textarea" :placeholder="t('problems.statementPlaceholder')" :auto-size="{ minRows: 11, maxRows: 18 }" />
            <div class="editor-count">{{ t('problems.charCount', { count: form.statement.length, max: 20000 }) }}</div>
          </article>

          <article class="sample-section">
            <div class="section-title">
              <div>
                <h2>{{ t('problems.testCases') }}</h2>
                <p>{{ t('problems.sampleSectionCopy') }}</p>
              </div>
              <a-button type="primary" size="small" @click="addCase">{{ t('problems.addCase') }}</a-button>
            </div>

            <div class="case-list">
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
                <div class="sample-grid">
                  <a-form-item :label="t('problems.input')">
                    <a-textarea v-model="testCase.input" class="sample-textarea" :auto-size="{ minRows: 4, maxRows: 8 }" />
                  </a-form-item>
                  <a-form-item :label="t('problems.expectedOutput')">
                    <a-textarea v-model="testCase.expectedOutput" class="sample-textarea" :auto-size="{ minRows: 4, maxRows: 8 }" />
                  </a-form-item>
                </div>
              </section>
            </div>
          </article>

          <a-alert type="info" show-icon class="problem-editor-alert" :content="t('problems.sampleHelp')" />
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
import { api, type Difficulty, type EntityId, type ProblemPayload, type ProblemResponse, type TestCaseDto } from '@aioj/api-client';
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
const form = reactive<ProblemPayload>({
  title: '',
  difficulty: 'EASY',
  statement: '',
  tags: [],
  testCases: [emptyCase(true)],
  timeLimitMillis: 1000,
  memoryLimitKb: 262144
});
const statementTools = ['H', 'B', 'I', 'S', '•', '1.', '</>', '🔗', '▧', '↶', '↷'];

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
});

const memoryLimitMb = computed({
  get: () => Math.round(form.memoryLimitKb / 1024),
  set: (value: number) => {
    form.memoryLimitKb = Number(value || 0) * 1024;
  }
});

const tagPreview = computed(() => parseTags());

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

function difficultyIcon(difficulty: Difficulty) {
  return ({ EASY: '●', MEDIUM: '◆', HARD: '▲', CHALLENGE: '✦' } as Record<Difficulty, string>)[difficulty];
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
  form.tags = [];
  form.testCases = [emptyCase(true)];
  form.timeLimitMillis = 1000;
  form.memoryLimitKb = 262144;
  tagText.value = '';
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
    form.tags = [...detail.tags];
    form.testCases = detail.samples.length ? detail.samples.map((item, index) => ({ ...item, sample: index === 0 ? true : item.sample })) : [emptyCase(true)];
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
  const testCases = form.testCases.map((testCase, index) => ({
    input: testCase.input,
    expectedOutput: testCase.expectedOutput,
    sample: index === 0 ? true : testCase.sample
  }));
  return {
    title: form.title.trim(),
    difficulty: form.difficulty,
    statement: form.statement.trim(),
    tags: parseTags(),
    testCases,
    timeLimitMillis: Number(form.timeLimitMillis || 1000),
    memoryLimitKb: Number(form.memoryLimitKb || 262144)
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
  const problemPayload = payload();
  const validationError = validatePayload(problemPayload);
  if (validationError) {
    Message.warning(validationError);
    return;
  }
  if (problemPayload.testCases.some((testCase) => !testCase.input.trim() || !testCase.expectedOutput.trim())) {
    Message.warning(t('problems.emptySampleWarning'));
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
    visibleProxy.value = false;
    emit('saved');
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('problems.saveFailed'));
  } finally {
    saving.value = false;
  }
}
</script>
