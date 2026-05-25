<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <p>{{ t('common.product') }}</p>
        <h1>{{ t('auth.adminTitle') }}</h1>
        <span>{{ t('auth.adminCopy') }}</span>
      </div>

      <a-card class="auth-card" :bordered="false">
        <h2>{{ t('auth.signIn') }}</h2>
        <a-alert
          v-if="route.query.expired"
          type="warning"
          show-icon
          class="form-alert"
          :content="t('auth.expired')"
        />
        <a-alert
          v-if="error"
          type="error"
          show-icon
          class="form-alert"
          :content="error"
        />
        <a-alert
          v-if="route.query.registered"
          type="success"
          show-icon
          class="form-alert"
          :content="t('auth.adminRegisteredAlert')"
        />
        <a-form :model="form" layout="vertical" @submit.prevent>
          <a-form-item :label="t('common.account')">
            <a-input v-model="form.account" :placeholder="t('auth.adminAccountPlaceholder')" autocomplete="username" />
          </a-form-item>
          <a-form-item :label="t('common.password')">
            <a-input-password v-model="form.password" :placeholder="t('auth.passwordPlaceholder')" autocomplete="current-password" />
          </a-form-item>
          <a-button type="primary" long :loading="loading" @click="submit">{{ t('auth.signIn') }}</a-button>
        </a-form>

        <p class="auth-switch">
          {{ t('auth.adminNoAccount') }}
          <router-link :to="{ name: 'register' }">{{ t('auth.register') }}</router-link>
        </p>
      </a-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { ApiError } from '@aioj/api-client';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { t } = useI18n();

const form = reactive({
  account: '',
  password: ''
});
const loading = ref(false);
const error = ref('');

onMounted(() => {
  if (typeof route.query.account === 'string') {
    form.account = route.query.account;
  }
});

async function submit() {
  loading.value = true;
  error.value = '';
  try {
    await auth.login(form.account.trim(), form.password);
    if (!auth.isAdmin) {
      await router.replace({ name: 'blocked' });
      return;
    }
    Message.success(t('auth.adminWelcome', { name: auth.displayName || t('shell.adminFallback') }));
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : { name: 'dashboard' });
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('auth.adminSignInFailed'));
  } finally {
    loading.value = false;
  }
}
</script>
