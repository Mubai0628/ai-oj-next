<template>
  <a-drawer v-model:visible="visibleProxy" :width="920" unmount-on-close class="draft-detail-drawer">
    <template #title>{{ t('drafts.detailTitle') }}</template>

    <template v-if="currentDraft">
      <header class="draft-detail-head">
        <div>
          <p class="draft-detail-eyebrow">#{{ currentDraft.id }}</p>
          <h2>{{ currentDraft.title }}</h2>
          <a-space wrap>
            <a-tag :color="difficultyColor(currentDraft.difficulty)">{{ t(`difficulty.${currentDraft.difficulty}`) }}</a-tag>
            <a-tag :color="currentDraft.validationStatus === 'VALID' ? 'green' : 'orange'">{{ validationStatusLabel(currentDraft.validationStatus) }}</a-tag>
            <a-tag>{{ currentDraft.model }}</a-tag>
            <a-tag>{{ tokenText }}</a-tag>
          </a-space>
        </div>
        <a-button v-if="currentDraft.refinedFromDraftId" type="outline" @click="emit('open-draft', currentDraft.refinedFromDraftId)">
          {{ t('drafts.chainParentLabel', { id: currentDraft.refinedFromDraftId }) }}
        </a-button>
      </header>

      <a-tabs v-model:active-key="activeTab" type="rounded" class="draft-detail-tabs">
        <a-tab-pane key="preview" :title="t('drafts.detailTabsPreview')">
          <div class="draft-preview-panel">
            <a-alert
              v-if="currentDraft.validationErrors?.length"
              type="warning"
              show-icon
              class="form-alert"
            >
              <ul class="draft-error-list">
                <li v-for="error in currentDraft.validationErrors" :key="error">{{ error }}</li>
              </ul>
            </a-alert>
            <MdPreview :model-value="currentDraft.statement || ''" language="zh-CN" preview-theme="github" code-theme="github" />
            <a-table :data="currentDraft.testCases" :pagination="false" size="small" class="draft-test-table">
              <template #columns>
                <a-table-column :title="t('problems.input')">
                  <template #cell="{ record }"><pre>{{ record.input }}</pre></template>
                </a-table-column>
                <a-table-column :title="t('problems.output')">
                  <template #cell="{ record }"><pre>{{ record.expectedOutput }}</pre></template>
                </a-table-column>
                <a-table-column :title="t('problems.sample')" :width="90">
                  <template #cell="{ record }"><a-tag :color="record.sample ? 'green' : 'gray'">{{ record.sample ? 'sample' : 'hidden' }}</a-tag></template>
                </a-table-column>
              </template>
            </a-table>
          </div>
        </a-tab-pane>

        <a-tab-pane key="edit" :title="t('drafts.detailTabsEdit')">
          <a-form :model="form" layout="vertical" class="draft-edit-form">
            <div class="draft-edit-grid">
              <a-form-item :label="t('common.title')">
                <a-input v-model="form.title" :max-length="120" />
              </a-form-item>
              <a-form-item :label="t('common.difficulty')">
                <a-select v-model="form.difficulty">
                  <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
                    {{ t(`difficulty.${difficulty}`) }}
                  </a-option>
                </a-select>
              </a-form-item>
              <a-form-item :label="t('common.tags')" class="draft-edit-wide">
                <a-input v-model="tagText" placeholder="comma,separated,tags" />
              </a-form-item>
              <a-form-item :label="t('problems.timeLimit')">
                <a-input-number v-model="form.timeLimitMillis" :min="100" :step="100" />
              </a-form-item>
              <a-form-item :label="t('problems.memoryLimit')">
                <a-input-number v-model="form.memoryLimitKb" :min="16384" :step="1024" />
              </a-form-item>
            </div>

            <a-form-item :label="t('problems.statementLabel')">
              <MdEditor
                v-model="form.statement"
                language="zh-CN"
                preview-theme="github"
                code-theme="github"
                :toolbars="editorToolbars"
                class="draft-md-editor"
              />
            </a-form-item>

            <section class="draft-cases-section">
              <div class="draft-cases-head">
                <strong>{{ t('problems.testCases') }}</strong>
                <a-button size="small" @click="addCase">{{ t('problems.addCase') }}</a-button>
              </div>
              <article v-for="(testCase, index) in form.testCases" :key="index" class="draft-case-card">
                <div class="draft-case-head">
                  <span>{{ t('problems.caseTitle', { index: index + 1 }) }}</span>
                  <a-space>
                    <a-checkbox v-model="testCase.sample">{{ t('problems.sample') }}</a-checkbox>
                    <a-button size="mini" status="danger" :disabled="form.testCases.length === 1" @click="removeCase(index)">
                      {{ t('common.remove') }}
                    </a-button>
                  </a-space>
                </div>
                <div class="draft-case-grid">
                  <a-form-item :label="t('problems.input')">
                    <a-textarea v-model="testCase.input" :auto-size="{ minRows: 3, maxRows: 8 }" />
                  </a-form-item>
                  <a-form-item :label="t('problems.expectedOutput')">
                    <a-textarea v-model="testCase.expectedOutput" :auto-size="{ minRows: 3, maxRows: 8 }" />
                  </a-form-item>
                </div>
              </article>
            </section>

            <a-form-item :label="t('drafts.refineNoteLabel')">
              <a-textarea v-model="form.refineNote" :max-length="500" :placeholder="t('drafts.refineNotePlaceholder')" :auto-size="{ minRows: 3, maxRows: 6 }" />
            </a-form-item>
            <a-button type="primary" :loading="refining" @click="refineDraft">{{ t('drafts.refineSubmit') }}</a-button>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="regenerate" :title="t('drafts.detailTabsRegenerate')">
          <a-form :model="{ feedback }" layout="vertical" class="draft-regenerate-form">
            <a-form-item :label="t('drafts.regenerateFeedbackLabel')">
              <a-textarea
                v-model="feedback"
                :max-length="500"
                :placeholder="t('drafts.regenerateFeedbackPlaceholder')"
                :auto-size="{ minRows: 8, maxRows: 12 }"
              />
            </a-form-item>
            <a-button type="primary" :loading="regenerating" @click="regenerateDraft">{{ t('drafts.regenerateSubmit') }}</a-button>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </template>

    <template #footer>
      <a-space v-if="currentDraft" wrap>
        <a-button @click="emitDraft('approve')">{{ t('drafts.approve') }}</a-button>
        <a-popconfirm :content="t('drafts.importConfirm')" @ok="emitDraft('import-draft')">
          <a-button type="primary" :disabled="!!currentDraft.importedProblemId">{{ t('drafts.import') }}</a-button>
        </a-popconfirm>
        <a-popconfirm :content="t('drafts.rejectConfirm')" @ok="emitDraft('reject')">
          <a-button status="warning" :disabled="!!currentDraft.importedProblemId">{{ t('drafts.reject') }}</a-button>
        </a-popconfirm>
        <a-popconfirm :content="t('drafts.deleteConfirm')" @ok="emitDraft('delete')">
          <a-button type="outline" status="danger" :disabled="!!currentDraft.importedProblemId">{{ t('common.delete') }}</a-button>
        </a-popconfirm>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import type { ToolbarNames } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ApiError, api, type Difficulty, type EntityId, type ProblemDraftResponse, type TestCaseDto } from '@aioj/api-client';

type DraftAction = 'approve' | 'import-draft' | 'reject' | 'delete';

const props = defineProps<{ visible: boolean; draft: ProblemDraftResponse | null }>();
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'refined', draft: ProblemDraftResponse): void;
  (event: 'regenerated', draft: ProblemDraftResponse): void;
  (event: 'approve', draft: ProblemDraftResponse): void;
  (event: 'import-draft', draft: ProblemDraftResponse): void;
  (event: 'reject', draft: ProblemDraftResponse): void;
  (event: 'delete', draft: ProblemDraftResponse): void;
  (event: 'open-draft', id: EntityId): void;
}>();

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const activeTab = ref('preview');
const localDraft = ref<ProblemDraftResponse | null>(null);
const feedback = ref('');
const refining = ref(false);
const regenerating = ref(false);
const tagText = ref('');
const form = reactive({
  title: '',
  difficulty: 'EASY' as Difficulty | string,
  statement: '',
  testCases: [] as TestCaseDto[],
  timeLimitMillis: 1000,
  memoryLimitKb: 262144,
  refineNote: ''
});

const editorToolbars: ToolbarNames[] = ['bold', 'italic', 'title', 'quote', 'unorderedList', 'orderedList', 'code', 'table', 'preview', 'fullscreen'];
const visibleProxy = computed({ get: () => props.visible, set: (value: boolean) => emit('update:visible', value) });
const currentDraft = computed(() => localDraft.value || props.draft);
const tokenText = computed(() => currentDraft.value ? `${currentDraft.value.promptTokens}/${currentDraft.value.completionTokens} tokens` : '');

watch(() => props.draft, (draft) => {
  localDraft.value = null;
  if (draft) resetForm(draft);
}, { immediate: true });

function resetForm(draft: ProblemDraftResponse) {
  form.title = draft.title;
  form.difficulty = draft.difficulty;
  form.statement = draft.statement || '';
  form.testCases = draft.testCases?.length ? draft.testCases.map((item) => ({ ...item })) : [emptyCase(true)];
  form.timeLimitMillis = draft.timeLimitMillis || 1000;
  form.memoryLimitKb = draft.memoryLimitKb || 262144;
  form.refineNote = draft.refineNote || '';
  tagText.value = (draft.tags || []).join(', ');
  feedback.value = '';
}

function emptyCase(sample = false): TestCaseDto {
  return { input: '', expectedOutput: '', sample };
}

function addCase() {
  form.testCases.push(emptyCase(false));
}

function removeCase(index: number) {
  if (form.testCases.length === 1) return;
  form.testCases.splice(index, 1);
  if (!form.testCases.some((item) => item.sample)) form.testCases[0].sample = true;
}

function parseTags() {
  return tagText.value.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
}

function difficultyColor(difficulty: string) {
  return ({ EASY: 'green', MEDIUM: 'orange', HARD: 'red', CHALLENGE: 'purple' } as Record<string, string>)[difficulty] || 'arcoblue';
}

function validationStatusLabel(status: string) {
  const key = `validationStatus.${status}`;
  const translated = t(key);
  return translated === key ? status : translated;
}

function emitDraft(action: DraftAction) {
  if (!currentDraft.value) return;
  if (action === 'approve') emit('approve', currentDraft.value);
  if (action === 'import-draft') emit('import-draft', currentDraft.value);
  if (action === 'reject') emit('reject', currentDraft.value);
  if (action === 'delete') emit('delete', currentDraft.value);
}

async function refineDraft() {
  if (!currentDraft.value) return;
  refining.value = true;
  try {
    const draft = await api.refineDraft(currentDraft.value.id, {
      title: form.title.trim(),
      difficulty: form.difficulty,
      statement: form.statement,
      tags: parseTags(),
      testCases: form.testCases.map((item) => ({ ...item })),
      timeLimitMillis: Number(form.timeLimitMillis || 1000),
      memoryLimitKb: Number(form.memoryLimitKb || 262144),
      refineNote: form.refineNote.trim() || undefined
    });
    localDraft.value = draft;
    resetForm(draft);
    Message.success(t('drafts.refinedMessage', { id: draft.id }));
    emit('refined', draft);
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.refineFailed')));
  } finally {
    refining.value = false;
  }
}

async function regenerateDraft() {
  if (!currentDraft.value || !feedback.value.trim()) return;
  regenerating.value = true;
  try {
    const draft = await api.regenerateDraft(currentDraft.value.id, feedback.value.trim());
    localDraft.value = draft;
    resetForm(draft);
    Message.success(t('drafts.regeneratedMessage', { id: draft.id }));
    emit('regenerated', draft);
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.regenerateFailed')));
  } finally {
    regenerating.value = false;
  }
}
</script>
