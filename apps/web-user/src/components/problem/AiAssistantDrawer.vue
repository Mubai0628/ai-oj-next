<template>
  <Teleport to="body">
    <Transition name="ai-drawer-fade">
      <div v-if="open" class="ai-drawer" role="presentation" @click.self="close">
        <Transition name="ai-drawer-slide" appear>
          <aside class="ai-drawer__panel" role="dialog" aria-modal="true" :aria-label="t('problems.aiGuidance')">
            <header class="ai-drawer__header">
              <div>
                <span>{{ t('problems.aiDrawerEyebrow') }}</span>
                <h2>{{ t('problems.aiGuidance') }}</h2>
                <p>{{ t('problems.aiGuidanceCopy') }}</p>
              </div>
              <button class="ai-drawer__close" type="button" :aria-label="t('common.cancel')" @click="close">×</button>
            </header>

            <AiHintPanel
              :prompt="prompt"
              :answer="answer"
              :loading="loading"
              :error="error"
              :show-header="false"
              @update:prompt="$emit('update:prompt', $event)"
              @ask="$emit('ask')"
              @quick="$emit('quick', $event)"
              @clear-error="$emit('clear-error')"
            />
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import AiHintPanel from '@/components/problem/AiHintPanel.vue';

const props = defineProps<{
  open: boolean;
  prompt: string;
  answer: string;
  loading: boolean;
  error: string;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  'update:prompt': [value: string];
  ask: [];
  quick: [value: string];
  'clear-error': [];
}>();

const { t } = useI18n();

function close() {
  emit('update:open', false);
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') close();
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      window.addEventListener('keydown', onKeydown);
    } else {
      window.removeEventListener('keydown', onKeydown);
    }
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
});
</script>
