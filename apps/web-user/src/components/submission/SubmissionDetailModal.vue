<template>
  <a-modal
    v-model:visible="visibleProxy"
    :title="t('submissions.viewCodeTitle')"
    :footer="false"
    :width="780"
    :mask-closable="true"
    :esc-to-close="true"
    :render-to-body="true"
    :popup-container="'body'"
    unmount-on-close
    modal-class="submission-detail-modal"
  >
    <a-spin :loading="loading" style="display: block;">
      <a-alert v-if="error" type="error" show-icon style="margin-bottom: 12px;">
        {{ error }}
      </a-alert>
      <template v-else-if="detail">
        <div class="submission-detail-meta">
          <div><span>{{ t('submissions.viewProblemLabel') }}</span><strong>#{{ detail.problemId }}</strong></div>
          <div><span>{{ t('submissions.viewLanguageLabel') }}</span><strong>{{ detail.language || '-' }}</strong></div>
          <div>
            <span>{{ t('submissions.viewStatusLabel') }}</span>
            <strong>{{ t(`submissionStatus.${detail.status}`) }}</strong>
          </div>
          <div><span>{{ t('submissions.viewTimeLabel') }}</span><strong>{{ detail.timeMillis ?? '-' }} ms</strong></div>
          <div><span>{{ t('submissions.viewMemoryLabel') }}</span><strong>{{ detail.memoryKb ?? '-' }} KB</strong></div>
          <div v-if="detail.runTimeMillis != null">
            <span>{{ t('submissions.viewRunTimeLabel') }}</span>
            <strong>{{ detail.runTimeMillis }} ms</strong>
          </div>
          <div v-if="detail.exitStatus != null">
            <span>{{ t('submissions.viewExitStatusLabel') }}</span>
            <strong>{{ detail.exitStatus }}</strong>
          </div>
        </div>
        <a-alert
          v-if="detail.judgeMessage"
          type="info"
          show-icon
          class="submission-detail-judge-msg"
        >
          {{ t('submissions.viewJudgeMessage') }}：{{ detail.judgeMessage }}
        </a-alert>
        <div v-if="detail.stderrExcerpt" class="submission-detail-stderr">
          <strong>{{ t('submissions.viewStderrLabel') }}</strong>
          <pre class="submission-detail-stderr-body">{{ detail.stderrExcerpt }}</pre>
        </div>
        <div class="submission-detail-code-body">
          <MdPreview
            v-if="detail.code"
            :model-value="detailCodeMarkdown"
            language="zh-CN"
            preview-theme="github"
            code-theme="github"
          />
          <a-empty v-else :description="t('submissions.viewCodeUnavailable')" />
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ApiError, api, type EntityId, type SubmissionResponse } from '@aioj/api-client';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';

const props = defineProps<{
  visible: boolean;
  submissionId: EntityId | null;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const detail = ref<SubmissionResponse | null>(null);
let requestSeq = 0;

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
});

const detailCodeMarkdown = computed(() => {
  if (!detail.value?.code) return '';
  const lang = (detail.value.language || '').toLowerCase();
  return ['```' + lang, detail.value.code, '```'].join('\n');
});

async function loadDetail(id: EntityId) {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const nextDetail = await api.submission(id);
    if (seq === requestSeq && props.visible && props.submissionId === id) {
      detail.value = nextDetail;
    }
  } catch (err) {
    if (seq === requestSeq && props.visible && props.submissionId === id) {
      error.value = err instanceof ApiError
        ? err.userMessage
        : err instanceof Error
          ? err.message
          : t('submissions.viewLoadFailed');
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

watch(
  [() => props.visible, () => props.submissionId],
  ([visible, submissionId]) => {
    if (!visible) {
      requestSeq++;
      detail.value = null;
      error.value = '';
      loading.value = false;
      return;
    }
    if (!submissionId) return;
    if (detail.value?.id === submissionId) return;
    void loadDetail(submissionId);
  },
  { immediate: true }
);
</script>
