<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <p>{{ t('common.product') }}</p>
        <h1>{{ t('auth.adminRegisterTitle') }}</h1>
        <span>{{ t('auth.adminRegisterCopy') }}</span>
      </div>

      <a-card class="auth-card" :bordered="false">
        <h2>{{ t('auth.register') }}</h2>
        <a-alert
          v-if="error"
          type="error"
          show-icon
          class="form-alert"
          :content="error"
        />
        <a-form :model="registerForm" layout="vertical" @submit.prevent>
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

        <p class="auth-switch">
          {{ t('auth.adminAlreadyAccount') }}
          <router-link :to="{ name: 'login' }">{{ t('auth.signIn') }}</router-link>
        </p>
      </a-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { api, authStore, type Role } from '@aioj/api-client';

const router = useRouter();
const { t } = useI18n();

const registerForm = reactive({
  account: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  email: '',
  role: 'STUDENT' as Extract<Role, 'STUDENT' | 'TEACHER'>
});
const registering = ref(false);
const error = ref('');

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
  authStore.clear();
  const account = registerForm.account.trim();
  try {
    const tokens = await api.register({
      account,
      password: registerForm.password,
      displayName: registerForm.displayName.trim(),
      email: registerForm.email.trim() || undefined,
      role: registerForm.role
    });
    authStore.save(tokens);
    try {
      await api.logout();
    } catch {
      // Best effort only: the user must sign in again after public registration.
    }
    authStore.clear();
    Message.success(t('auth.adminRegisterSuccess'));
    await router.replace({ name: 'login', query: { registered: '1', account } });
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('auth.registerFailed');
  } finally {
    registering.value = false;
  }
}
</script>
