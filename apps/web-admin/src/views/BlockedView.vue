<template>
  <main class="blocked-page">
    <a-card class="blocked-card" :bordered="false">
      <a-tag color="red">{{ t('blocked.tag') }}</a-tag>
      <h1>{{ t('blocked.title') }}</h1>
      <p>
        {{ t('blocked.description', { name: auth.profile?.displayName || auth.profile?.account || t('blocked.thisAccount') }) }}
      </p>
      <a-space wrap>
        <a-button type="primary" :loading="loading" @click="logout">{{ t('common.logout') }}</a-button>
        <a-button @click="goLogin">{{ t('blocked.useAnother') }}</a-button>
      </a-space>
    </a-card>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();
const { t } = useI18n();
const loading = ref(false);

async function logout() {
  loading.value = true;
  try {
    await auth.logout();
    await router.replace({ name: 'login' });
  } finally {
    loading.value = false;
  }
}

async function goLogin() {
  auth.clearLocal();
  await router.replace({ name: 'login' });
}
</script>
