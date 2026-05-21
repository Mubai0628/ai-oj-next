<template>
  <main class="auth-page">
    <section class="auth-panel">
      <p class="eyebrow">{{ t('common.product') }}</p>
      <h1>{{ t('auth.createStudentTitle') }}</h1>
      <a-form layout="vertical" :model="form" @submit.prevent="submit">
        <a-form-item :label="t('common.account')">
          <a-input v-model="form.account" :placeholder="t('auth.accountPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('common.displayName')">
          <a-input v-model="form.displayName" :placeholder="t('auth.displayNamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('common.email')">
          <a-input v-model="form.email" :placeholder="t('auth.emailPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('common.password')">
          <a-input-password v-model="form.password" :placeholder="t('auth.passwordRulePlaceholder')" />
        </a-form-item>
        <a-button type="primary" long :loading="loading" @click="submit">{{ t('auth.register') }}</a-button>
      </a-form>
      <p class="auth-switch">
        {{ t('auth.alreadyHaveAccount') }}
        <router-link to="/login">{{ t('auth.signIn') }}</router-link>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const form = reactive({
  account: '',
  displayName: '',
  email: '',
  password: ''
});

async function submit() {
  if (!form.account.trim() || !form.displayName.trim() || form.password.length < 6) {
    Message.warning(t('auth.registerRequired'));
    return;
  }
  loading.value = true;
  try {
    await auth.register({
      account: form.account.trim(),
      displayName: form.displayName.trim(),
      email: form.email.trim() || undefined,
      password: form.password
    });
    Message.success(t('auth.accountCreated'));
    await router.replace('/');
  } catch (error) {
    Message.error(error instanceof Error ? error.message : t('auth.registerFailed'));
  } finally {
    loading.value = false;
  }
}
</script>
