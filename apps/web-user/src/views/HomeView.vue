<template>
  <main class="dashboard-page">
    <BaseCard class="dashboard-hero">
      <div class="dashboard-hero__copy">
        <p class="dashboard-hero__eyebrow">{{ t('dashboard.eyebrow') }}</p>
        <h1 class="dashboard-hero__title">{{ greeting }}</h1>
        <p class="dashboard-hero__desc">{{ t('dashboard.heroSubtitle') }}</p>
        <div class="dashboard-hero__actions">
          <router-link class="dashboard-action dashboard-action--primary" to="/problems">{{ t('dashboard.startPractice') }}</router-link>
          <router-link class="dashboard-action dashboard-action--ghost" to="/submissions">{{ t('dashboard.viewSubmissions') }}</router-link>
        </div>
      </div>
      <div class="dashboard-hero__visual" aria-hidden="true">
        <div class="hero-visual-card hero-visual-card--code">
          <span>&lt;/&gt;</span>
          <i />
          <i />
          <i />
        </div>
        <div class="hero-visual-card hero-visual-card--chart">
          <svg viewBox="0 0 120 56" role="img">
            <polyline points="8,42 28,32 48,36 70,20 92,26 112,12" />
            <circle cx="70" cy="20" r="4" />
            <circle cx="112" cy="12" r="4" />
          </svg>
        </div>
        <div class="hero-laptop">
          <span />
        </div>
      </div>
    </BaseCard>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>

    <a-spin :loading="loading" :tip="t('dashboard.loading')">
      <section class="stats-grid">
        <router-link v-for="card in statCards" :key="card.key" class="stat-card base-card interactive" :to="card.to">
          <div class="stat-card__header">
            <span class="stat-card__label">{{ card.label }}</span>
            <span class="stat-card__icon" :class="`stat-card__icon--${card.icon}`" aria-hidden="true" />
          </div>
          <strong class="stat-card__value" :class="{ 'stat-card__value--text': card.valueText }">{{ card.value }}</strong>
          <StatusChip v-if="card.chipLabel" :label="card.chipLabel" :tone="card.chipTone" />
          <small v-else class="stat-card__meta" :class="`tone-${card.tone}`">{{ card.meta }}</small>
        </router-link>
      </section>

      <section class="dashboard-content">
        <BaseCard class="recommend-card">
          <div class="recommend-card__header">
            <h2>{{ t('dashboard.recommended') }}</h2>
            <router-link to="/problems" class="text-link">{{ t('dashboard.viewAll') }}</router-link>
          </div>
          <EmptyState v-if="!recommendedProblems.length" :title="t('dashboard.noRecommendedTitle')" :description="t('dashboard.noRecommendedDesc')">
            <router-link class="dashboard-action dashboard-action--ghost" to="/problems">{{ t('dashboard.goProblems') }}</router-link>
          </EmptyState>
          <div v-else class="recommend-list">
            <router-link v-for="problem in recommendedProblems" :key="problem.id" class="recommend-row" :to="`/problems/${problem.id}`">
              <div class="recommend-row__main">
                <h3>{{ problem.title }}</h3>
                <p>{{ problem.description }}</p>
                <div class="recommend-row__tags">
                  <span v-for="tag in problem.tags" :key="tag" class="recommend-tag">{{ tag }}</span>
                </div>
              </div>
              <div class="recommend-row__side">
                <StatusChip :label="difficultyLabel(problem.difficulty)" :status="problem.difficulty" />
                <span>{{ problem.passRateText }}</span>
                <strong>{{ t('dashboard.practice') }} <i aria-hidden="true" /></strong>
              </div>
            </router-link>
          </div>
        </BaseCard>

        <div class="recent-column">
          <BaseCard class="recent-card">
            <div class="recent-card__header">
              <h2>{{ t('dashboard.recentWork') }}</h2>
              <router-link to="/submissions" class="text-link">{{ t('nav.submissions') }}</router-link>
            </div>
            <div v-if="submissions.length" class="recent-practice">
              <div class="recent-summary">
                <strong>{{ t('dashboard.weeklySubmissions', { count: weeklySubmissionCount }) }}</strong>
                <span>{{ t('dashboard.weeklyAccepted', { count: weeklyAcceptedCount }) }}</span>
              </div>
              <svg class="mini-trend-chart" viewBox="0 0 220 84" role="img" :aria-label="t('dashboard.recentWork')">
                <polyline class="mini-trend-chart__grid" points="0,66 220,66" />
                <polyline class="mini-trend-chart__line" :points="trendPolyline" />
                <circle
                  v-for="point in trendDots"
                  :key="point.key"
                  class="mini-trend-chart__dot"
                  :cx="point.x"
                  :cy="point.y"
                  r="3.5"
                />
              </svg>
              <div class="recent-list">
                <router-link v-for="item in submissions.slice(0, 4)" :key="item.id" class="recent-row" to="/submissions">
                  <span>{{ statusLabel(item.status) }}</span>
                  <strong>{{ t('dashboard.problemLabel', { id: shortId(item.problemId) }) }}</strong>
                  <small>{{ item.language }} · {{ formatDate(item.createdAt) }}</small>
                </router-link>
              </div>
            </div>
            <div v-else class="recent-empty">
              <div class="recent-empty__icon" aria-hidden="true" />
              <strong>{{ t('dashboard.noSubmissions') }}</strong>
              <p>{{ t('dashboard.startTimeline') }}</p>
              <router-link class="dashboard-action dashboard-action--ghost" to="/problems">{{ t('dashboard.firstProblem') }}</router-link>
            </div>
          </BaseCard>

          <BaseCard class="achievement-card">
            <div class="recent-card__header">
              <h2>{{ t('dashboard.achievements') }}</h2>
            </div>
            <div class="achievement-list">
              <div v-for="item in achievements" :key="item.key" class="achievement-item">
                <span class="achievement-item__icon" :class="`achievement-item__icon--${item.icon}`" aria-hidden="true" />
                <div>
                  <strong>{{ item.label }}</strong>
                  <small>{{ item.value }}</small>
                </div>
              </div>
            </div>
          </BaseCard>
        </div>
      </section>
    </a-spin>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api, type Difficulty, type EntityId, type ProblemResponse, type SubmissionResponse, type SubmissionStatus } from '@aioj/api-client';
import BaseCard from '@/components/common/BaseCard.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import StatusChip from '@/components/common/StatusChip.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const problems = ref<ProblemResponse[]>([]);
const submissions = ref<SubmissionResponse[]>([]);

type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
interface RecommendedProblem {
  id: EntityId;
  title: string;
  description: string;
  difficulty: Difficulty;
  tags: string[];
  passRateText: string;
}
interface RecentPracticePoint {
  key: string;
  label: string;
  count: number;
  acceptedCount: number;
}

const acceptedCount = computed(() => submissions.value.filter((item) => item.status === 'ACCEPTED').length);
const latestStatus = computed(() => submissions.value[0] ? statusLabel(submissions.value[0].status) : t('dashboard.noSubmissions'));
const displayName = computed(() => auth.profile?.displayName || auth.profile?.account || t('shell.studentFallback'));
const acceptedRate = computed(() => {
  if (!submissions.value.length) return t('dashboard.noSubmissions');
  return `${Math.round((acceptedCount.value / submissions.value.length) * 100)}%`;
});
const acceptedRateNumber = computed(() => {
  if (!submissions.value.length) return 0;
  return Math.round((acceptedCount.value / submissions.value.length) * 100);
});
const acceptedRateText = computed(() => {
  if (!submissions.value.length) return t('dashboard.today');
  return t('dashboard.passRate', { rate: acceptedRateNumber.value });
});
const submissionMeta = computed(() => submissions.value.length ? t('dashboard.cumulativeSubmissions') : t('dashboard.noSubmissions'));
const latestStatusTone = computed<StatTone>(() => {
  const status = submissions.value[0]?.status;
  if (!status) return 'neutral';
  if (status === 'ACCEPTED') return 'success';
  if (status === 'QUEUED' || status === 'RUNNING') return 'primary';
  return 'danger';
});
const greeting = computed(() => t('dashboard.greetingWithWave', { name: displayName.value }));
const statCards = computed(() => [
  {
    key: 'problems',
    label: t('dashboard.problemsAvailable'),
    value: problems.value.length,
    meta: t('dashboard.healthProblems'),
    tone: 'success' as StatTone,
    icon: 'book',
    to: '/problems'
  },
  {
    key: 'submissions',
    label: t('dashboard.mySubmissions'),
    value: submissions.value.length,
    meta: submissionMeta.value,
    tone: (submissions.value.length ? 'primary' : 'neutral') as StatTone,
    icon: 'send',
    to: '/submissions'
  },
  {
    key: 'accepted',
    label: t('dashboard.accepted'),
    value: acceptedCount.value,
    meta: acceptedRateText.value,
    tone: acceptedCount.value ? 'success' : 'neutral' as StatTone,
    icon: 'check',
    to: '/submissions'
  },
  {
    key: 'latest',
    label: t('dashboard.latestStatus'),
    value: latestStatus.value,
    valueText: true,
    meta: latestStatus.value,
    chipLabel: latestStatus.value,
    chipTone: latestStatusTone.value,
    tone: latestStatusTone.value,
    icon: 'status',
    to: '/submissions'
  }
]);
const recommendedProblems = computed<RecommendedProblem[]>(() =>
  problems.value.slice(0, 6).map((problem) => ({
    id: problem.id,
    title: problem.title,
    description: preview(problem.statement),
    difficulty: problem.difficulty,
    tags: problem.tags.slice(0, 3),
    passRateText: t('dashboard.passRateUnknown')
  }))
);
const todaySubmissionCount = computed(() => submissions.value.filter((item) => isSameLocalDate(new Date(item.createdAt), new Date())).length);
const recentPracticePoints = computed<RecentPracticePoint[]>(() => {
  const today = startOfLocalDay(new Date());
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (6 - index));
    const key = dateKey(date);
    const daySubmissions = submissions.value.filter((item) => dateKey(new Date(item.createdAt)) === key);
    return {
      key,
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      count: daySubmissions.length,
      acceptedCount: daySubmissions.filter((item) => item.status === 'ACCEPTED').length
    };
  });
});
const weeklySubmissionCount = computed(() => recentPracticePoints.value.reduce((sum, point) => sum + point.count, 0));
const weeklyAcceptedCount = computed(() => recentPracticePoints.value.reduce((sum, point) => sum + point.acceptedCount, 0));
const trendDots = computed(() => {
  const max = Math.max(1, ...recentPracticePoints.value.map((point) => point.count));
  return recentPracticePoints.value.map((point, index) => ({
    key: point.key,
    x: 12 + index * 32.5,
    y: 66 - (point.count / max) * 46
  }));
});
const trendPolyline = computed(() => trendDots.value.map((point) => `${point.x},${point.y}`).join(' '));
const achievements = computed(() => [
  {
    key: 'streak',
    icon: 'streak',
    label: t('dashboard.streak'),
    value: t('dashboard.streakValue', { days: 0 })
  },
  {
    key: 'accepted',
    icon: 'accepted',
    label: t('dashboard.totalAccepted'),
    value: t('dashboard.totalAcceptedValue', { count: acceptedCount.value })
  },
  {
    key: 'today',
    icon: 'today',
    label: t('dashboard.todaySubmissions'),
    value: t('dashboard.todaySubmissionsValue', { count: todaySubmissionCount.value })
  }
]);

function shortId(id: EntityId) {
  const text = String(id);
  return text.length > 8 ? text.slice(-8) : text;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function preview(statement: string) {
  return statement.replace(/[#*_`]/g, '').replace(/\s+/g, ' ').trim().slice(0, 82) || t('problems.openDetailFallback');
}

function difficultyLabel(difficulty: Difficulty) {
  return t(`difficulty.${difficulty}`);
}

function statusLabel(status: SubmissionStatus) {
  return t(`submissionStatus.${status}`);
}

function startOfLocalDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function dateKey(date: Date) {
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
}

function isSameLocalDate(left: Date, right: Date) {
  return dateKey(left) === dateKey(right);
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [problemPage, submissionPage] = await Promise.all([
      api.problems({ page: 1, pageSize: 12 }),
      api.mySubmissions({ page: 1, pageSize: 8 })
    ]);
    problems.value = problemPage.records;
    submissions.value = submissionPage.records;
    await auth.loadProfile(true);
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('dashboard.loadFailed');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
