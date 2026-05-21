<template>
  <main class="student-layout">
    <AppSidebar
      :items="navItems"
      :subtitle="t('common.userProduct')"
      :nav-label="t('shell.userNavLabel')"
      :display-name="displayName"
      :role-label="roleLabel"
      :initial="userInitial"
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
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
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
const router = useRouter();
const loggingOut = ref(false);

const navItems = computed<SidebarItem[]>(() => [
  { to: '/', label: t('nav.home'), icon: 'H' },
  { to: '/problems', label: t('nav.problems'), icon: 'P' },
  { to: '/submissions', label: t('nav.submissions'), icon: 'S' },
  { to: '/ai-chat', label: t('nav.aiChat'), icon: 'A' },
  { to: '/profile', label: t('nav.profile'), icon: 'U' }
]);

const displayName = computed(() => auth.profile?.displayName || t('shell.studentFallback'));
const roleLabel = computed(() => auth.profile?.roles.map((role) => t(`role.${role}`)).join(', ') || t('shell.studentFallback'));
const userInitial = computed(() => (auth.profile?.displayName || auth.profile?.account || t('shell.studentFallback')).slice(0, 1));
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
</script>
