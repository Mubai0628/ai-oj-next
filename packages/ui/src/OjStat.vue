<template>
  <section class="oj-stat">
    <span>{{ label }}</span>
    <strong>{{ value }}</strong>
  </section>
</template>

<script setup lang="ts">
defineProps<{ label: string; value: string | number }>();
</script>

<style scoped>
.oj-stat {
  display: grid;
  gap: 6px;
  min-height: 82px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.05);
}

.oj-stat span {
  color: #667085;
  font-size: 13px;
}

.oj-stat strong {
  color: #111827;
  font-size: 28px;
  line-height: 1.1;
}
</style>
