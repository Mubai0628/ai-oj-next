<template>
  <main class="admin-layout">
    <aside class="sidebar">
      <div class="brand">
        <span>AI</span>
        <div>
          <strong>{{ t('common.appName') }}</strong>
          <small>{{ t('common.admin') }}</small>
        </div>
      </div>

      <nav class="nav-list" :aria-label="t('shell.adminNavLabel')">
        <RouterLink v-for="item in navItems" :key="item.name" :to="{ name: item.name }" class="nav-item">
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <section class="workspace">
      <OjToolbar :eyebrow="t('common.adminConsole')" :title="currentTitle">
        <a-space wrap>
          <LanguageSwitcher />
          <a-tag color="green">{{ t('role.ADMIN') }}</a-tag>
          <span class="user-name">{{ auth.displayName || t('shell.adminFallback') }}</span>
          <a-button size="small" :loading="loggingOut" @click="logout">{{ t('common.logout') }}</a-button>
        </a-space>
      </OjToolbar>
      <router-view />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { LanguageSwitcher } from '@aioj/i18n';
import { OjToolbar } from '@aioj/ui';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loggingOut = ref(false);

const navItems = computed(() => [
  { name: 'dashboard', label: t('nav.dashboard') },
  { name: 'users', label: t('nav.users') },
  { name: 'roles', label: t('nav.roles') },
  { name: 'problems', label: t('nav.problems') },
  { name: 'ai-drafts', label: t('nav.aiDrafts') }
]);

const currentTitle = computed(() => navItems.value.find((item) => item.name === route.name)?.label || t('nav.dashboard'));

async function logout() {
  loggingOut.value = true;
  try {
    await auth.logout();
    await router.replace({ name: 'login' });
  } finally {
    loggingOut.value = false;
  }
}
</script>
