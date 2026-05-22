<template>
  <article class="problem-card problem-pane">
    <div class="problem-card__intro">
      <span class="problem-id">#{{ problem.id }}</span>
      <div class="problem-card__intro-main">
        <h2>{{ problem.title }}</h2>
        <DifficultyChip :difficulty="problem.difficulty" />
      </div>
      <ProblemMetaChips :problem="problem" />
    </div>

    <ProblemTabs :model-value="activeTab" @update:model-value="$emit('update:activeTab', $event)" />

    <section v-if="activeTab === 'statement'" class="problem-tab-panel">
      <div class="problem-section problem-section--statement">
        <h3 class="problem-section__title">{{ t('problems.descriptionTab') }}</h3>
        <div class="markdown-body problem-statement" v-html="statementHtml" />
      </div>

      <div class="problem-short-support">
        <section class="problem-support-card">
          <div class="problem-support-card__header">
            <h3>{{ t('problems.publicSamplePreview') }}</h3>
            <button v-if="problem.samples.length" type="button" @click="$emit('update:activeTab', 'samples')">
              {{ t('problems.samplesTab') }}
            </button>
          </div>
          <SampleCaseCard
            v-if="problem.samples[0]"
            :sample="problem.samples[0]"
            :index="1"
            @copy="$emit('copy-sample', $event)"
          />
          <EmptyState v-else :description="t('problems.noSamples')" />
        </section>

        <section class="problem-support-card">
          <h3>{{ t('problems.workspaceHintsTitle') }}</h3>
          <details class="problem-details" open>
            <summary>{{ t('problems.constraintSummary') }}</summary>
            <div class="problem-note-grid problem-note-grid--compact">
              <span>{{ t('problems.timeLimit') }}<strong>{{ problem.timeLimitMillis }} ms</strong></span>
              <span>{{ t('problems.memoryLimit') }}<strong>{{ problem.memoryLimitMb }} MB</strong></span>
            </div>
          </details>
          <details class="problem-details">
            <summary>{{ t('problems.hintSummary') }}</summary>
            <p>{{ t('problems.hintSummaryCopy') }}</p>
          </details>
        </section>
      </div>
    </section>

    <section v-else-if="activeTab === 'samples'" class="problem-tab-panel">
      <EmptyState v-if="!problem.samples.length" :description="t('problems.noSamples')" />
      <div v-else class="sample-list">
        <SampleCaseCard
          v-for="(sample, index) in problem.samples"
          :key="`${index}-${sample.input}-${sample.output}`"
          :sample="sample"
          :index="index + 1"
          @copy="$emit('copy-sample', $event)"
        />
      </div>
    </section>

    <section v-else-if="activeTab === 'notes'" class="problem-tab-panel">
      <div class="problem-section">
        <h3 class="problem-section__title">{{ t('problems.notesTitle') }}</h3>
        <p class="problem-section__content">{{ t('problems.notesCopy') }}</p>
      </div>
      <div class="problem-section">
        <h3 class="problem-section__title">{{ t('problems.limits') }}</h3>
        <div class="problem-note-grid">
          <span>{{ t('problems.timeLimit') }}<strong>{{ problem.timeLimitMillis }} ms</strong></span>
          <span>{{ t('problems.memoryLimit') }}<strong>{{ problem.memoryLimitMb }} MB</strong></span>
          <span>{{ t('common.created') }}<strong>{{ formatDate(problem.createdAt) }}</strong></span>
        </div>
      </div>
    </section>

    <section v-else class="problem-tab-panel">
      <EmptyState :title="t('problems.relatedEmptyTitle')" :description="t('problems.relatedEmptyDescription')" />
    </section>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import ProblemMetaChips from '@/components/problem/ProblemMetaChips.vue';
import ProblemTabs from '@/components/problem/ProblemTabs.vue';
import SampleCaseCard from '@/components/problem/SampleCaseCard.vue';
import type { ProblemDetailModel, ProblemTabKey } from '@/types/problem-workspace';

defineProps<{
  problem: ProblemDetailModel;
  activeTab: ProblemTabKey;
  statementHtml: string;
}>();

defineEmits<{
  'update:activeTab': [value: ProblemTabKey];
  'copy-sample': [value: string];
}>();

const { t } = useI18n();

function formatDate(value?: string) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}
</script>
