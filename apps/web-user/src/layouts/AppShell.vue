<template>
  <main class="student-layout">
    <template v-if="sessionValid">
      <AppSidebar
        :items="navItems"
        :subtitle="t('common.userProduct')"
        :nav-label="t('shell.userNavLabel')"
      />

      <section class="app-workspace">
        <AppTopActions
          :user="topUser"
          :logging-out="loggingOut"
          :search-label="t('shell.search')"
          :notification-label="t('shell.notifications')"
          :theme-label="t('shell.theme')"
          @logout="logout"
        />
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
import { useRoute, useRouter } from 'vue-router';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import AppTopActions from '@/components/layout/AppTopActions.vue';
import { useAuthStore } from '@/stores/auth';

interface SidebarItem {
  to: string;
  label: string;
  icon: string;
}

const auth = useAuthStore();
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const loggingOut = ref(false);

const sessionValid = computed(() => {
  void auth.authTick;
  return auth.isAuthenticated && Boolean(auth.profile);
});

watch(sessionValid, (good) => {
  if (good) return;
  if (route.name === 'login' || route.name === 'register') return;
  router.replace({ name: 'login', query: { expired: '1', redirect: route.fullPath } })
    .catch(() => {});
}, { immediate: false });

const navItems = computed<SidebarItem[]>(() => [
  { to: '/', label: t('nav.home'), icon: 'H' },
  { to: '/problems', label: t('nav.problems'), icon: 'P' },
  { to: '/ai-chat', label: t('nav.aiChat'), icon: 'A' }
]);

const roleLabel = computed(() => auth.profile?.roles.map((role) => t(`role.${role}`)).join(', ') || t('shell.studentFallback'));
const topUser = computed(() => ({
  id: auth.profile?.userId,
  name: auth.profile?.displayName || auth.profile?.account || t('shell.studentFallback'),
  role: roleLabel.value,
  email: auth.profile?.email
}));

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
