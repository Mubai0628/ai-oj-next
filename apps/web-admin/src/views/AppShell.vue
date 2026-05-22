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
          <span class="nav-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <section class="workspace">
      <OjToolbar :eyebrow="t('common.adminConsole')" :title="currentTitle">
        <a-space class="admin-top-actions" wrap>
          <LanguageSwitcher />
          <a-dropdown trigger="click" position="br" @select="handleUserMenu">
            <button class="admin-user-trigger" type="button">
              <span class="admin-avatar">{{ userInitial }}</span>
              <span class="admin-user-copy">
                <strong>{{ auth.displayName || t('shell.adminFallback') }}</strong>
                <small>{{ t('role.ADMIN') }}</small>
              </span>
              <span class="admin-user-caret">⌄</span>
            </button>
            <template #content>
              <a-doption value="dashboard">{{ t('nav.dashboard') }}</a-doption>
              <a-doption value="logout">
                <span class="danger-menu-item">{{ loggingOut ? t('common.loading') : t('common.logout') }}</span>
              </a-doption>
            </template>
          </a-dropdown>
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
import { Modal } from '@arco-design/web-vue';
import { LanguageSwitcher } from '@aioj/i18n';
import { OjToolbar } from '@aioj/ui';
import { useAdminSessionGuard } from '@/composables/useAdminSessionGuard';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loggingOut = ref(false);
useAdminSessionGuard();

const navItems = computed(() => [
  { name: 'dashboard', label: t('nav.dashboard'), icon: 'D' },
  { name: 'users', label: t('nav.users'), icon: 'U' },
  { name: 'roles', label: t('nav.roles'), icon: 'R' },
  { name: 'problems', label: t('nav.problems'), icon: 'P' },
  { name: 'ai-drafts', label: t('nav.aiDrafts'), icon: 'AI' }
]);

const currentTitle = computed(() => navItems.value.find((item) => item.name === route.name)?.label || t('nav.dashboard'));
const userInitial = computed(() => (auth.displayName || t('shell.adminFallback')).trim().slice(0, 1).toUpperCase());

function handleUserMenu(value: string | number | Record<string, unknown> | undefined) {
  if (value === 'dashboard') {
    void router.push({ name: 'dashboard' });
  }
  if (value === 'logout') {
    confirmLogout();
  }
}

function confirmLogout() {
  Modal.warning({
    title: t('userMenu.logoutTitle'),
    content: t('userMenu.logoutDescription'),
    okText: t('common.logout'),
    cancelText: t('common.cancel'),
    hideCancel: false,
    onOk: () => logout()
  });
}

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
