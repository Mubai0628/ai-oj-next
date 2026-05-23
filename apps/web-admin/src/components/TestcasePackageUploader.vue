<template>
  <section class="testcase-uploader">
    <div class="section-title">
      <div>
        <h2>{{ t('testcase.title') }}</h2>
        <p>{{ t('testcase.subtitle') }}</p>
      </div>
      <a-button :loading="packagesLoading" @click="loadPackages">{{ t('common.refresh') }}</a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon class="form-alert" :content="error" />
    <a-alert v-if="!problemId" type="warning" show-icon class="form-alert" :content="t('testcase.noProblem')" />

    <div class="upload-panel">
      <label class="file-picker">
        <input ref="fileInput" type="file" accept=".zip,application/zip,application/x-zip-compressed" @change="selectFile" />
        <span>{{ t('testcase.selectZip') }}</span>
      </label>
      <a-button type="primary" :disabled="!problemId || !selectedFile || busy" :loading="busy" @click="uploadSelected">
        {{ t('testcase.upload') }}
      </a-button>
      <a-button v-if="canRetry" :disabled="busy" @click="uploadSelected">{{ t('testcase.retry') }}</a-button>
    </div>

    <div v-if="selectedFile" class="file-facts">
      <span>{{ t('testcase.selectedFile') }}</span>
      <strong>{{ selectedFile.name }}</strong>
      <span>{{ t('testcase.fileSize') }}</span>
      <strong>{{ formatBytes(selectedFile.size) }}</strong>
      <span>{{ t('testcase.sha256') }}</span>
      <strong class="hash-text">{{ fileSha256 || phaseText }}</strong>
      <span>{{ t('testcase.chunkSize') }}</span>
      <strong>{{ formatBytes(chunkSizeBytes) }}</strong>
      <span>{{ t('testcase.totalChunks') }}</span>
      <strong>{{ totalChunks || '-' }}</strong>
    </div>

    <div v-if="busy || uploadStatus" class="upload-status">
      <div class="progress-head">
        <span>{{ t('testcase.uploadProgress') }}</span>
        <strong>{{ Math.round(progressPercent) }}%</strong>
      </div>
      <progress :value="progressPercent" max="100" />
      <p>{{ phaseText }}</p>
      <p v-if="uploadStatus">
        {{ t('testcase.uploadedChunks') }}:
        {{ uploadStatus.uploadedChunks.length }} / {{ uploadStatus.totalChunks }}
      </p>
    </div>

    <section class="package-summary">
      <h3>{{ t('testcase.activePackage') }}</h3>
      <a-empty v-if="!activePackage" :description="t('testcase.noPackages')" />
      <article v-else class="package-card active">
        <div>
          <strong>{{ activePackage.version }}</strong>
          <span>{{ activePackage.fileName }}</span>
        </div>
        <a-tag color="green">{{ packageStatusLabel(activePackage.status) }}</a-tag>
      </article>
    </section>

    <section class="package-summary">
      <h3>{{ t('testcase.packages') }}</h3>
      <a-spin :loading="packagesLoading">
        <a-empty v-if="!packages.length" :description="t('testcase.noPackages')" />
        <div v-else class="package-list">
          <article v-for="item in packages" :key="item.id" class="package-card">
            <div class="package-main">
              <strong>{{ item.version }}</strong>
              <span>{{ item.fileName }} · {{ formatBytes(item.fileSizeBytes) }}</span>
              <small>{{ t('testcase.caseCount', { count: item.caseCount }) }} · {{ t('testcase.sampleCount', { count: item.sampleCount }) }}</small>
              <small v-if="item.errorMessage">{{ t('testcase.errorMessage') }}: {{ item.errorMessage }}</small>
            </div>
            <a-space>
              <a-tag :color="packageStatusColor(item.status)">{{ packageStatusLabel(item.status) }}</a-tag>
              <a-tag v-if="item.active" color="green">{{ t('common.active') }}</a-tag>
              <a-button
                v-else
                size="small"
                :disabled="item.status !== 'READY'"
                :loading="activatingId === item.id"
                @click="activatePackage(item.id)"
              >
                {{ t('testcase.activate') }}
              </a-button>
            </a-space>
          </article>
        </div>
      </a-spin>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import {
  api,
  type EntityId,
  type TestcasePackageResponse,
  type TestcasePackageStatus,
  type TestcaseUploadStatusResponse
} from '@aioj/api-client';

const props = defineProps<{ problemId: EntityId | null }>();
const emit = defineEmits<{ uploaded: [value: TestcasePackageResponse]; activated: [value: TestcasePackageResponse] }>();

const { t } = useI18n();
const chunkSizeBytes = 4 * 1024 * 1024;
const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const fileSha256 = ref('');
const uploadStatus = ref<TestcaseUploadStatusResponse | null>(null);
const packages = ref<TestcasePackageResponse[]>([]);
const packagesLoading = ref(false);
const busy = ref(false);
const canRetry = ref(false);
const error = ref('');
const phase = ref<'idle' | 'hashing' | 'uploading' | 'completing' | 'polling' | 'ready' | 'failed'>('idle');
const activatingId = ref<EntityId | null>(null);

const totalChunks = computed(() => selectedFile.value ? Math.ceil(selectedFile.value.size / chunkSizeBytes) : 0);
const activePackage = computed(() => packages.value.find((item) => item.active) || null);
const progressPercent = computed(() => {
  if (!uploadStatus.value) return phase.value === 'hashing' ? 0 : 0;
  return uploadStatus.value.progress <= 1 ? uploadStatus.value.progress * 100 : uploadStatus.value.progress;
});
const phaseText = computed(() => {
  if (phase.value === 'hashing') return t('testcase.computing');
  if (phase.value === 'uploading') return t('testcase.uploading');
  if (phase.value === 'completing') return t('testcase.completing');
  if (phase.value === 'polling') return t('testcase.polling');
  if (phase.value === 'ready') return t('testcase.uploadReady');
  if (phase.value === 'failed') return t('testcase.uploadFailed');
  return '-';
});

function bytesToHex(buffer: ArrayBuffer) {
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

async function sha256(blob: Blob) {
  const buffer = await blob.arrayBuffer();
  return bytesToHex(await crypto.subtle.digest('SHA-256', buffer));
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

function packageStatusLabel(status: TestcasePackageStatus) {
  return t(`packageStatus.${status}`);
}

function packageStatusColor(status: TestcasePackageStatus) {
  if (status === 'READY') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'PROCESSING') return 'orange';
  return 'arcoblue';
}

function clearSelectedFile() {
  selectedFile.value = null;
  fileSha256.value = '';
  if (fileInput.value) fileInput.value.value = '';
}

async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  error.value = '';
  canRetry.value = false;
  uploadStatus.value = null;
  fileSha256.value = '';

  if (!file) {
    selectedFile.value = null;
    phase.value = 'idle';
    return;
  }

  if (!file.name.toLowerCase().endsWith('.zip')) {
    selectedFile.value = null;
    phase.value = 'failed';
    error.value = t('testcase.onlyZip');
    if (fileInput.value) fileInput.value.value = '';
    return;
  }

  const MAX_BYTES = 200 * 1024 * 1024;
  if (file.size > MAX_BYTES) {
    selectedFile.value = null;
    phase.value = 'failed';
    error.value = t('testcase.fileTooLarge');
    if (fileInput.value) fileInput.value.value = '';
    return;
  }

  selectedFile.value = file;
  phase.value = 'hashing';
  fileSha256.value = await sha256(file);
  phase.value = 'idle';
}

async function uploadSelected() {
  if (!props.problemId) {
    error.value = t('testcase.noProblem');
    return;
  }
  if (!selectedFile.value) {
    error.value = t('testcase.noFile');
    return;
  }

  busy.value = true;
  canRetry.value = false;
  error.value = '';
  uploadStatus.value = null;

  try {
    if (!fileSha256.value) {
      phase.value = 'hashing';
      fileSha256.value = await sha256(selectedFile.value);
    }

    const init = await api.initTestcasePackage(props.problemId, {
      fileName: selectedFile.value.name,
      fileSizeBytes: selectedFile.value.size,
      sha256: fileSha256.value,
      chunkSizeBytes,
      totalChunks: totalChunks.value
    });

    let status: TestcaseUploadStatusResponse = {
      uploadId: init.uploadId,
      status: init.status,
      uploadedChunks: init.uploadedChunks,
      totalChunks: init.totalChunks,
      progress: init.totalChunks ? init.uploadedChunks.length / init.totalChunks : 0,
      packageId: init.packageId
    };
    uploadStatus.value = status;

    phase.value = 'uploading';
    const uploaded = new Set(init.uploadedChunks);
    for (let index = 0; index < init.totalChunks; index += 1) {
      if (uploaded.has(index)) continue;
      const start = index * chunkSizeBytes;
      const chunk = selectedFile.value.slice(start, Math.min(selectedFile.value.size, start + chunkSizeBytes));
      const chunkSha256 = await sha256(chunk);
      status = await api.uploadTestcaseChunk(props.problemId, init.uploadId, index, chunk, chunkSha256);
      uploadStatus.value = status;
    }

    phase.value = 'completing';
    const completed = await api.completeTestcaseUpload(props.problemId, init.uploadId);
    emit('uploaded', completed);

    if (completed.status === 'READY') {
      phase.value = 'ready';
      Message.success(t('testcase.uploadReady'));
      clearSelectedFile();
      await loadPackages();
      return;
    }
    if (completed.status === 'FAILED') {
      throw new Error(completed.errorMessage || t('testcase.uploadFailed'));
    }

    await pollStatus(init.uploadId);
    await loadPackages();
  } catch (caught) {
    phase.value = 'failed';
    canRetry.value = true;
    error.value = caught instanceof Error ? caught.message : t('testcase.initFailed');
  } finally {
    busy.value = false;
  }
}

async function pollStatus(uploadId: string) {
  if (!props.problemId) return;
  phase.value = 'polling';
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const status = await api.testcaseUploadStatus(props.problemId, uploadId);
    uploadStatus.value = status;
    if (status.status === 'READY') {
      phase.value = 'ready';
      Message.success(t('testcase.uploadReady'));
      clearSelectedFile();
      return;
    }
    if (status.status === 'FAILED') {
      throw new Error(status.errorMessage || t('testcase.uploadFailed'));
    }
    await new Promise((resolve) => window.setTimeout(resolve, 2000));
  }
  throw new Error(t('testcase.pollTimeout'));
}

async function loadPackages() {
  if (!props.problemId) {
    packages.value = [];
    return;
  }
  packagesLoading.value = true;
  try {
    packages.value = await api.testcasePackages(props.problemId);
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('testcase.listFailed');
  } finally {
    packagesLoading.value = false;
  }
}

async function activatePackage(packageId: EntityId) {
  if (!props.problemId) return;
  activatingId.value = packageId;
  error.value = '';
  try {
    const activated = await api.activateTestcasePackage(props.problemId, packageId);
    emit('activated', activated);
    Message.success(t('testcase.activated'));
    await loadPackages();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('testcase.activateFailed');
  } finally {
    activatingId.value = null;
  }
}

watch(() => props.problemId, loadPackages, { immediate: true });
</script>
