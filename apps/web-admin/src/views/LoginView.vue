<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <p>{{ t('common.product') }}</p>
        <h1>{{ t('auth.adminTitle') }}</h1>
        <span>{{ t('auth.adminCopy') }}</span>
      </div>

      <a-card class="auth-card" :bordered="false">
        <a-tabs v-model:active-key="activeTab" type="rounded">
          <a-tab-pane key="login" :title="t('auth.signIn')" />
          <a-tab-pane key="register" :title="t('auth.register')" />
        </a-tabs>
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
        <a-form v-if="activeTab === 'login'" :model="form" layout="vertical" @submit.prevent>
          <a-form-item :label="t('common.account')">
            <a-input v-model="form.account" :placeholder="t('auth.adminAccountPlaceholder')" autocomplete="username" />
          </a-form-item>
          <a-form-item :label="t('common.password')">
            <a-input-password v-model="form.password" :placeholder="t('auth.passwordPlaceholder')" autocomplete="current-password" />
          </a-form-item>
          <a-button type="primary" long :loading="loading" @click="submit">{{ t('auth.signIn') }}</a-button>
        </a-form>

        <a-form v-else :model="registerForm" layout="vertical" @submit.prevent>
          <a-form-item :label="t('common.account')">
            <a-input v-model="registerForm.account" :placeholder="t('auth.accountPlaceholder')" autocomplete="username" />
          </a-form-item>
          <a-form-item :label="t('common.displayName')">
            <a-input v-model="registerForm.displayName" :placeholder="t('auth.displayNamePlaceholder')" />
          </a-form-item>
          <a-form-item :label="t('common.email')">
            <a-input v-model="registerForm.email" :placeholder="t('auth.emailPlaceholder')" autocomplete="email" />
          </a-form-item>
          <a-form-item :label="t('auth.selectRole')">
            <a-radio-group v-model="registerForm.role" type="button">
              <a-radio value="STUDENT">{{ t('auth.studentRole') }}</a-radio>
              <a-radio value="TEACHER">{{ t('auth.teacherRole') }}</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item :label="t('common.password')">
            <a-input-password v-model="registerForm.password" :placeholder="t('auth.adminPasswordRulePlaceholder')" autocomplete="new-password" />
          </a-form-item>
          <a-form-item :label="t('auth.confirmPassword')">
            <a-input-password v-model="registerForm.confirmPassword" :placeholder="t('auth.confirmPassword')" autocomplete="new-password" />
          </a-form-item>
          <a-button type="primary" long :loading="registering" @click="register">{{ t('auth.register') }}</a-button>
        </a-form>
      </a-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { api, authStore, type Role } from '@aioj/api-client';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { t } = useI18n();

const form = reactive({
  account: '',
  password: ''
});
const registerForm = reactive({
  account: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  email: '',
  role: 'STUDENT' as Extract<Role, 'STUDENT' | 'TEACHER'>
});
const activeTab = ref<'login' | 'register'>('login');
const loading = ref(false);
const registering = ref(false);
const error = ref('');

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
    error.value = caught instanceof Error ? caught.message : t('auth.adminSignInFailed');
  } finally {
    loading.value = false;
  }
}

function validateRegister() {
  const email = registerForm.email.trim();
  if (registerForm.account.trim().length < 3) return t('auth.accountTooShort');
  if (!registerForm.displayName.trim()) return t('auth.displayNameRequired');
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return t('auth.emailInvalid');
  if (registerForm.password.length < 8) return t('auth.adminPasswordTooShort');
  if (registerForm.password !== registerForm.confirmPassword) return t('auth.passwordMismatch');
  if (registerForm.role !== 'STUDENT' && registerForm.role !== 'TEACHER') return t('auth.roleRequired');
  return '';
}

async function register() {
  const validation = validateRegister();
  if (validation) {
    error.value = validation;
    return;
  }
  registering.value = true;
  error.value = '';
  try {
    await api.register({
      account: registerForm.account.trim(),
      password: registerForm.password,
      displayName: registerForm.displayName.trim(),
      email: registerForm.email.trim() || undefined,
      role: registerForm.role
    });
    authStore.clear();
    Message.success(t('auth.adminRegisterSuccess'));
    activeTab.value = 'login';
    form.account = registerForm.account.trim();
    form.password = '';
    registerForm.password = '';
    registerForm.confirmPassword = '';
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('auth.registerFailed');
  } finally {
    registering.value = false;
  }
}
</script>
