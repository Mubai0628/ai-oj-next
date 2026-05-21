<template>
  <div class="empty-state">
    <div class="empty-visual">
      <span />
    </div>
    <strong>{{ title || description }}</strong>
    <p v-if="title">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  description: string;
}>();
</script>

<style scoped>
.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 28px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-visual {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary-soft);
}

.empty-visual span {
  width: 22px;
  height: 16px;
  border: 2px solid var(--color-primary);
  border-top: 0;
  border-radius: 0 0 8px 8px;
}

.empty-state strong {
  color: var(--color-text-secondary);
  font-size: 15px;
}

.empty-state p {
  max-width: 420px;
  margin: 0;
  line-height: 1.6;
}
</style>
