<template>
  <component :is="as" class="base-card" :class="{ interactive }">
    <slot />
  </component>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  as?: string;
  interactive?: boolean;
}>(), {
  as: 'section',
  interactive: false
});
</script>

<style scoped>
.base-card {
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.base-card.interactive {
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.base-card.interactive:hover {
  border-color: rgba(37, 99, 235, 0.22);
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}
</style>
