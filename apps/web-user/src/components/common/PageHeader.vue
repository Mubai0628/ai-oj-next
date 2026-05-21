<template>
  <header class="page-header">
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="page-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow?: string;
  title: string;
  description?: string;
}>();
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 4px 0 0;
  color: var(--color-text);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.18;
}

.page-description {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.page-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 720px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
