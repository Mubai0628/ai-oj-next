<template>
  <a-select v-model="currentLocale" class="language-switcher" size="small" :aria-label="$t('locale.label')">
    <a-option v-for="option in localeOptions" :key="option.value" :value="option.value">
      {{ option.label }}
    </a-option>
  </a-select>
</template>

<script setup lang="ts">
import { localeOptions, useLocaleSwitcher } from './locale';

const { currentLocale } = useLocaleSwitcher();
</script>

<style scoped>
.language-switcher {
  width: 124px;
}
</style>
