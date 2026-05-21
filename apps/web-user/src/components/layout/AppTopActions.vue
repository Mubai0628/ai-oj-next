<template>
  <header class="app-top-actions">
    <button class="icon-button" type="button" :aria-label="searchLabel" :title="searchLabel">
      <span class="top-icon top-icon-search" />
    </button>
    <button class="icon-button" type="button" :aria-label="notificationLabel" :title="notificationLabel">
      <span class="top-icon top-icon-bell" />
    </button>
    <button class="icon-button" type="button" :aria-label="themeLabel" :title="themeLabel">
      <span class="top-icon top-icon-theme" />
    </button>
    <LanguageSwitcher />
    <UserDropdown :user="user" :logging-out="loggingOut" @logout="$emit('logout')" />
  </header>
</template>

<script setup lang="ts">
import { LanguageSwitcher } from '@aioj/i18n';
import UserDropdown, { type UserMenuUser } from '@/components/layout/UserDropdown.vue';

defineEmits<{ logout: [] }>();

defineProps<{
  user: UserMenuUser;
  loggingOut: boolean;
  searchLabel: string;
  notificationLabel: string;
  themeLabel: string;
}>();
</script>
