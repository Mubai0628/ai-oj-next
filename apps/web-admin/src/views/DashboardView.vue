<template>
  <section class="view-stack">
    <div class="view-toolbar">
      <a-button :loading="loading" @click="loadDashboard">{{ t('common.refresh') }}</a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon :content="error" />
    <a-spin :loading="loading" :tip="t('dashboard.adminLoading')">
      <section v-if="hasStats" class="stats-grid">
        <OjStat :label="t('dashboard.users')" :value="stats.usersTotal" />
        <OjStat :label="t('dashboard.enabledUsers')" :value="stats.enabledUsers" />
        <OjStat :label="t('dashboard.problems')" :value="stats.problemsTotal" />
        <OjStat :label="t('dashboard.pendingDrafts')" :value="stats.pendingDrafts" />
        <OjStat :label="t('dashboard.approvedDrafts')" :value="stats.approvedDrafts" />
      </section>
      <a-empty v-else-if="!loading" :description="t('dashboard.noAdminData')" />
    </a-spin>

    <section class="dashboard-grid">
      <a-card :title="t('dashboard.aiUsage')" :bordered="false">
        <template v-if="usage">
          <div class="usage-row">
            <span>{{ t('dashboard.today') }}</span>
            <strong>{{ usage.usedToday }} / {{ usage.dailyLimit }}</strong>
          </div>
          <div class="meter"><i :style="{ width: dailyPercent }" /></div>
          <div class="usage-row">
            <span>{{ t('dashboard.thisMonth') }}</span>
            <strong>{{ usage.usedThisMonth }} / {{ usage.monthlyLimit }}</strong>
          </div>
          <div class="meter"><i :style="{ width: monthlyPercent }" /></div>
        </template>
        <a-empty v-else :description="t('dashboard.usageUnavailable')" />
      </a-card>

      <a-card :title="t('dashboard.localHealth')" :bordered="false">
        <div class="health-list">
          <div v-for="item in health" :key="item.label" class="health-item">
            <span>{{ item.label }}</span>
            <a-tag :color="item.ok ? 'green' : 'orange'">{{ item.ok ? t('common.ready') : t('common.check') }}</a-tag>
          </div>
        </div>
      </a-card>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { OjStat } from '@aioj/ui';
import { api, type AiUsageResponse } from '@aioj/api-client';

const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const usage = ref<AiUsageResponse | null>(null);
const loaded = ref(false);
const stats = reactive({
  usersTotal: 0,
  enabledUsers: 0,
  problemsTotal: 0,
  pendingDrafts: 0,
  approvedDrafts: 0
});

const hasStats = computed(() => loaded.value);
const dailyPercent = computed(() => percent(usage.value?.usedToday, usage.value?.dailyLimit));
const monthlyPercent = computed(() => percent(usage.value?.usedThisMonth, usage.value?.monthlyLimit));
const health = computed(() => [
  { label: t('dashboard.healthUsers'), ok: stats.usersTotal > 0 },
  { label: t('dashboard.healthProblems'), ok: stats.problemsTotal > 0 },
  { label: t('dashboard.healthDrafts'), ok: stats.pendingDrafts + stats.approvedDrafts > 0 },
  { label: t('dashboard.healthQuota'), ok: Boolean(usage.value) }
]);

function percent(used = 0, limit = 0) {
  if (!limit) return '0%';
  return `${Math.min(100, Math.round((used / limit) * 100))}%`;
}

function settledValue<T>(result: PromiseSettledResult<T>) {
  return result.status === 'fulfilled' ? result.value : null;
}

async function loadDashboard() {
  loading.value = true;
  error.value = '';
  try {
    const [usersResult, enabledUsersResult, problemsResult, pendingDraftsResult, approvedDraftsResult, usageResult] = await Promise.allSettled([
      api.users({ page: 1, pageSize: 1 }),
      api.users({ page: 1, pageSize: 1, enabled: true }),
      api.problems({ page: 1, pageSize: 1 }),
      api.problemDrafts({ page: 1, pageSize: 1, status: 'PENDING_REVIEW' }),
      api.problemDrafts({ page: 1, pageSize: 1, status: 'APPROVED' }),
      api.usage()
    ]);

    const users = settledValue(usersResult);
    const enabledUsers = settledValue(enabledUsersResult);
    const problems = settledValue(problemsResult);
    const pendingDrafts = settledValue(pendingDraftsResult);
    const approvedDrafts = settledValue(approvedDraftsResult);

    stats.usersTotal = users?.total ?? 0;
    stats.enabledUsers = enabledUsers?.total ?? 0;
    stats.problemsTotal = problems?.total ?? 0;
    stats.pendingDrafts = pendingDrafts?.total ?? 0;
    stats.approvedDrafts = approvedDrafts?.total ?? 0;
    usage.value = settledValue(usageResult);
    loaded.value = true;
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('dashboard.loadFailed');
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>
