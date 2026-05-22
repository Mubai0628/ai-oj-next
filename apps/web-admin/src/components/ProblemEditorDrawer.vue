<template>
  <a-drawer
    v-model:visible="visibleProxy"
    :title="editingId ? t('problems.editModal') : t('problems.createModal')"
    :width="980"
    :footer="false"
    class="problem-editor-drawer"
    unmount-on-close
  >
    <a-form :model="form" layout="vertical" class="problem-editor">
      <a-tabs v-model:active-key="activeTab" type="rounded" class="problem-editor-tabs">
        <a-tab-pane key="basic" :title="t('problems.editorBasic')" />
        <a-tab-pane key="statement" :title="t('problems.editorStatement')" />
        <a-tab-pane key="package" :title="t('problems.editorPackage')" />
      </a-tabs>

      <section v-if="activeTab === 'basic'" class="problem-editor-section">
        <div class="form-grid two">
          <a-form-item :label="t('problems.titleLabel')">
            <a-input v-model="form.title" />
          </a-form-item>
          <a-form-item :label="t('common.difficulty')">
            <a-select v-model="form.difficulty">
              <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
                {{ difficultyLabel(difficulty) }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('common.tags')">
            <a-input :model-value="tagText" :placeholder="t('problems.tagsPlaceholder')" @update:model-value="emit('update:tagText', String($event))" />
          </a-form-item>
          <div class="form-grid two compact">
            <a-form-item :label="t('problems.timeLimit')">
              <a-input-number v-model="form.timeLimitMillis" :min="100" :step="100" hide-button>
                <template #suffix>ms</template>
              </a-input-number>
            </a-form-item>
            <a-form-item :label="t('problems.memoryLimit')">
              <a-input-number :model-value="memoryLimitMb" :min="16" :step="16" hide-button @update:model-value="emit('update:memoryLimitMb', Number($event || 0))">
                <template #suffix>MB</template>
              </a-input-number>
            </a-form-item>
          </div>
        </div>
      </section>

      <section v-else-if="activeTab === 'statement'" class="problem-editor-section">
        <a-form-item :label="t('problems.statement')">
          <a-textarea v-model="form.statement" :auto-size="{ minRows: 12, maxRows: 20 }" />
        </a-form-item>
        <div class="section-title">
          <h2>{{ t('problems.testCases') }}</h2>
          <a-button size="small" @click="emit('add-case')">{{ t('problems.addCase') }}</a-button>
        </div>
        <div class="case-list">
          <section v-for="(testCase, index) in form.testCases" :key="index" class="case-card">
            <div class="case-head">
              <strong>{{ t('problems.caseTitle', { index: index + 1 }) }}</strong>
              <a-space>
                <a-checkbox v-model="testCase.sample">{{ t('problems.sample') }}</a-checkbox>
                <a-button size="mini" status="danger" :disabled="form.testCases.length === 1" @click="emit('remove-case', index)">
                  {{ t('common.remove') }}
                </a-button>
              </a-space>
            </div>
            <div class="form-grid two">
              <a-form-item :label="t('problems.input')">
                <a-textarea v-model="testCase.input" :auto-size="{ minRows: 4, maxRows: 8 }" />
              </a-form-item>
              <a-form-item :label="t('problems.expectedOutput')">
                <a-textarea v-model="testCase.expectedOutput" :auto-size="{ minRows: 4, maxRows: 8 }" />
              </a-form-item>
            </div>
          </section>
        </div>
      </section>

      <section v-else class="problem-editor-section">
        <a-alert
          v-if="!editingId"
          type="info"
          show-icon
          :content="t('problems.saveBeforeUploadPackage')"
        />
        <TestcasePackageUploader v-else :problem-id="editingId" />
      </section>
    </a-form>

    <div class="problem-editor-footer">
      <a-button @click="visibleProxy = false">{{ t('common.cancel') }}</a-button>
      <a-button type="primary" :loading="saving" @click="emit('save')">{{ t('common.save') }}</a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { Difficulty, EntityId, ProblemPayload } from '@aioj/api-client';
import TestcasePackageUploader from '@/components/TestcasePackageUploader.vue';

const props = defineProps<{
  visible: boolean;
  editingId: EntityId | null;
  form: ProblemPayload;
  tagText: string;
  memoryLimitMb: number;
  difficulties: Difficulty[];
  saving: boolean;
  difficultyLabel: (difficulty: Difficulty) => string;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  'update:tagText': [value: string];
  'update:memoryLimitMb': [value: number];
  save: [];
  'add-case': [];
  'remove-case': [index: number];
}>();

const { t } = useI18n();
const activeTab = ref<'basic' | 'statement' | 'package'>('basic');

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
});
</script>
