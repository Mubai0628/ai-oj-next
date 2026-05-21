<template>
  <main class="auth-page">
    <section class="auth-panel">
      <p class="eyebrow">{{ t('common.product') }}</p>
      <h1>{{ t('auth.studentLoginTitle') }}</h1>
      <a-alert v-if="route.query.expired" type="warning" class="auth-alert">
        {{ t('auth.expired') }}
      </a-alert>
      <a-form layout="vertical" :model="form" @submit.prevent="submit">
        <a-form-item :label="t('common.account')">
          <a-input v-model="form.account" :placeholder="t('auth.accountShortPlaceholder')" autocomplete="username" />
        </a-form-item>
        <a-form-item :label="t('common.password')">
          <a-input-password v-model="form.password" :placeholder="t('auth.passwordPlaceholder')" autocomplete="current-password" />
        </a-form-item>
        <a-button type="primary" long :loading="loading" @click="submit">{{ t('auth.login') }}</a-button>
      </a-form>
      <p class="auth-switch">
        {{ t('auth.newToAioj') }}
        <router-link to="/register">{{ t('auth.createAccount') }}</router-link>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const form = reactive({ account: '', password: '' });

async function submit() {
  if (!form.account.trim() || !form.password) {
    Message.warning(t('auth.accountPasswordRequired'));
    return;
  }
  loading.value = true;
  try {
    await auth.login(form.account.trim(), form.password);
    Message.success(t('auth.welcomeBack'));
    await router.replace(String(route.query.redirect || '/'));
  } catch (error) {
    Message.error(error instanceof Error ? error.message : t('auth.loginFailed'));
  } finally {
    loading.value = false;
  }
}
</script>
