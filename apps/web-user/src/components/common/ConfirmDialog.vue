<template>
  <Teleport to="body">
    <div v-if="open" class="confirm-dialog" role="presentation" @click.self="close">
      <section
        class="confirm-dialog__panel"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="descriptionId"
      >
        <div class="confirm-dialog__icon" :class="`tone-${tone}`" />
        <div class="confirm-dialog__body">
          <h2 :id="titleId">{{ title }}</h2>
          <p :id="descriptionId">{{ description }}</p>
        </div>
        <footer class="confirm-dialog__actions">
          <button class="confirm-dialog__button confirm-dialog__button--ghost" type="button" :disabled="loading" @click="cancel">
            {{ cancelLabel }}
          </button>
          <button
            class="confirm-dialog__button confirm-dialog__button--confirm"
            :class="`tone-${tone}`"
            type="button"
            :disabled="loading"
            @click="$emit('confirm')"
          >
            {{ loading ? loadingLabel : confirmLabel }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue';

const props = withDefaults(defineProps<{
  open: boolean;
  title: string;
  description: string;
  cancelLabel: string;
  confirmLabel: string;
  loadingLabel?: string;
  loading?: boolean;
  tone?: 'danger' | 'primary';
}>(), {
  loadingLabel: '',
  loading: false,
  tone: 'danger'
});

const emit = defineEmits<{
  'update:open': [value: boolean];
  cancel: [];
  confirm: [];
}>();

const titleId = computed(() => `confirm-title-${props.title.replace(/\s+/g, '-').toLowerCase()}`);
const descriptionId = computed(() => `${titleId.value}-description`);

function close() {
  if (!props.loading) emit('update:open', false);
}

function cancel() {
  emit('cancel');
  close();
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

<style scoped>
.confirm-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(8px);
}

.confirm-dialog__panel {
  width: min(420px, 100%);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  padding: 20px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 20px;
  background: var(--color-surface);
  box-shadow: 0 26px 80px rgba(15, 23, 42, 0.18);
}

.confirm-dialog__icon {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 50%;
}

.confirm-dialog__icon::before,
.confirm-dialog__icon::after {
  content: "";
  position: absolute;
  background: currentColor;
}

.confirm-dialog__icon::before {
  left: 19px;
  top: 10px;
  width: 4px;
  height: 17px;
  border-radius: 999px;
}

.confirm-dialog__icon::after {
  left: 19px;
  bottom: 9px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
}

.confirm-dialog__icon.tone-danger {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.confirm-dialog__icon.tone-primary {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.confirm-dialog__body h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 18px;
  font-weight: 800;
}

.confirm-dialog__body p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.65;
}

.confirm-dialog__actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 4px;
}

.confirm-dialog__button {
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 999px;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.confirm-dialog__button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.confirm-dialog__button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.confirm-dialog__button--ghost {
  border-color: var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.confirm-dialog__button--confirm.tone-danger {
  background: var(--color-danger);
  color: #fff;
}

.confirm-dialog__button--confirm.tone-primary {
  background: var(--color-primary);
  color: #fff;
}

@media (max-width: 560px) {
  .confirm-dialog__panel {
    grid-template-columns: 1fr;
  }

  .confirm-dialog__actions {
    flex-direction: column-reverse;
  }
}
</style>
