<template>
  <main class="admin-layout">
    <template v-if="sessionValid">
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

        <button class="sidebar-collapse" type="button">
          <span>‹</span>
          <strong>{{ t('shell.collapseMenu') }}</strong>
        </button>
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
                <div class="admin-menu-profile">
                  <span class="admin-avatar">{{ userInitial }}</span>
                  <div>
                    <strong>{{ auth.displayName || t('shell.adminFallback') }}</strong>
                    <small>{{ t('role.ADMIN') }}</small>
                  </div>
                </div>
                <a-doption value="dashboard">{{ t('nav.dashboard') }}</a-doption>
                <a-doption value="refresh">{{ t('userMenu.refreshProfile') }}</a-doption>
                <a-doption value="logout">
                  <span class="danger-menu-item">{{ loggingOut ? t('common.loading') : t('common.logout') }}</span>
                </a-doption>
              </template>
            </a-dropdown>
          </a-space>
        </OjToolbar>
        <router-view />
      </section>
    </template>
    <div v-else class="auth-overlay" role="alert" aria-live="polite">
      <article class="auth-overlay-card">
        <span class="auth-overlay-icon">🔒</span>
        <h2>{{ t('auth.sessionExpiredTitle') }}</h2>
        <p>{{ t('auth.sessionExpiredCopy') }}</p>
        <a-button type="primary" long @click="goLogin">{{ t('auth.signInAgain') }}</a-button>
      </article>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { Modal } from '@arco-design/web-vue';
import { LanguageSwitcher } from '@aioj/i18n';
import { OjToolbar } from '@aioj/ui';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loggingOut = ref(false);

const sessionValid = computed(() => {
  void auth.authTick;
  return auth.isAuthenticated && auth.isAdmin && Boolean(auth.profile);
});

watch(sessionValid, (good) => {
  if (good) return;
  if (route.name === 'login' || route.name === 'register') return;
  router.replace({ name: 'login', query: { expired: '1', redirect: route.fullPath } })
    .catch(() => {});
}, { immediate: false });

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
  if (value === 'refresh') {
    void auth.loadProfile(true);
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

function goLogin() {
  router.replace({ name: 'login', query: { redirect: route.fullPath } })
    .catch(() => {});
}
</script>
