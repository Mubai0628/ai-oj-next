<template>
  <header class="oj-toolbar">
    <div>
      <p>{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
    </div>
    <slot />
  </header>
</template>

<script setup lang="ts">
defineProps<{ eyebrow: string; title: string }>();
</script>

<style scoped>
.oj-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0 14px;
  border-bottom: 1px solid rgba(229, 231, 235, 0.72);
}

.oj-toolbar p {
  margin: 0 0 4px;
  color: #667085;
  font-size: 13px;
}

.oj-toolbar h1 {
  margin: 0;
  color: #101828;
  font-size: 25px;
  font-weight: 700;
  line-height: 1.18;
}

@media (max-width: 680px) {
  .oj-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
