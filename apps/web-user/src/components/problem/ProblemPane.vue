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
        <MdPreview
          :model-value="problem.statement || ''"
          language="zh-CN"
          preview-theme="github"
          code-theme="github"
        />
      </div>

      <div class="problem-section">
        <h3 class="problem-section__title">{{ t('problems.samplesTab') }}</h3>
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
      </div>

      <div class="problem-section">
        <h3 class="problem-section__title">{{ t('problems.notesTab') }}</h3>
        <template v-if="problem.notes">
          <MdPreview
            :model-value="problem.notes"
            language="zh-CN"
            preview-theme="github"
            code-theme="github"
          />
        </template>
        <template v-else>
          <p class="problem-section__content">{{ t('problems.notesCopy') }}</p>
          <p class="notes-empty">{{ t('problems.notesEmpty') }}</p>
        </template>
      </div>
    </section>

    <section v-else-if="activeTab === 'submissions'" class="problem-tab-panel">
      <ProblemSubmissionsPanel
        :problem-id="problem.id"
        @view="$emit('view-submission', $event)"
      />
    </section>

    <section v-else class="problem-tab-panel">
      <EmptyState :title="t('problems.relatedEmptyTitle')" :description="t('problems.relatedEmptyDescription')" />
    </section>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import ProblemMetaChips from '@/components/problem/ProblemMetaChips.vue';
import ProblemSubmissionsPanel from '@/components/problem/ProblemSubmissionsPanel.vue';
import ProblemTabs from '@/components/problem/ProblemTabs.vue';
import SampleCaseCard from '@/components/problem/SampleCaseCard.vue';
import type { EntityId } from '@aioj/api-client';
import type { ProblemDetailModel, ProblemTabKey } from '@/types/problem-workspace';

defineProps<{
  problem: ProblemDetailModel;
  activeTab: ProblemTabKey;
}>();

defineEmits<{
  'update:activeTab': [value: ProblemTabKey];
  'copy-sample': [value: string];
  'view-submission': [submissionId: EntityId];
}>();

const { t } = useI18n();
</script>
