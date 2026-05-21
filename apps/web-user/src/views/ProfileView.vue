<template>
  <section class="page-stack profile-page">
    <PageHeader :eyebrow="t('profile.eyebrow')" :title="t('profile.title')" :description="t('profile.description')" />

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>

    <nav class="profile-tabs" aria-label="profile settings">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="profile-tab"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <a-spin :loading="loading" :tip="t('profile.loading')">
      <section v-if="activeTab !== 'account'" class="profile-placeholder base-card">
        <EmptyState :title="t('profile.plannedTitle')" :description="t('profile.plannedDescription')" />
      </section>

      <section v-else class="profile-settings-grid">
        <article class="profile-card profile-card--identity">
          <div class="profile-avatar-card">
            <div class="profile-avatar">{{ initial }}</div>
            <button class="avatar-action" type="button" disabled>{{ t('profile.changeAvatar') }}</button>
          </div>
          <div class="profile-identity">
            <strong>{{ displayName }}</strong>
            <span>{{ auth.profile?.account || '-' }}</span>
          </div>
          <StatusChip :label="roleText" tone="primary" />

          <div class="profile-facts">
            <span>{{ t('profile.userId') }}</span>
            <strong>{{ auth.profile?.userId ?? '-' }}</strong>
            <span>{{ t('common.email') }}</span>
            <strong>{{ auth.profile?.email || '-' }}</strong>
            <span>{{ t('common.roles') }}</span>
            <strong>{{ roleText }}</strong>
          </div>
        </article>

        <article class="profile-card">
          <div class="profile-section-title">
            <h2>{{ t('profile.updateProfile') }}</h2>
            <p>{{ t('profile.updateProfileCopy') }}</p>
          </div>
          <a-form layout="vertical" :model="profileForm" @submit.prevent="saveProfile">
            <a-form-item :label="t('common.displayName')">
              <a-input v-model="profileForm.displayName" />
            </a-form-item>
            <a-form-item :label="t('common.email')">
              <a-input v-model="profileForm.email" />
            </a-form-item>
            <a-button type="primary" long :loading="savingProfile" @click="saveProfile">{{ t('profile.saveProfile') }}</a-button>
          </a-form>
        </article>

        <article class="profile-card profile-card--security">
          <div class="profile-section-title">
            <h2>{{ t('profile.accountSecurity') }}</h2>
            <p>{{ t('profile.securityCopy') }}</p>
          </div>
          <a-form layout="vertical" :model="passwordForm" @submit.prevent="changePassword">
            <a-form-item :label="t('profile.currentPassword')">
              <a-input-password v-model="passwordForm.currentPassword" />
              <p v-if="passwordErrors.currentPassword" class="form-error">{{ passwordErrors.currentPassword }}</p>
            </a-form-item>
            <a-form-item :label="t('profile.newPassword')">
              <a-input-password v-model="passwordForm.newPassword" />
              <p v-if="passwordErrors.newPassword" class="form-error">{{ passwordErrors.newPassword }}</p>
            </a-form-item>
            <a-form-item :label="t('profile.confirmPassword')">
              <a-input-password v-model="passwordForm.confirmPassword" />
              <p v-if="passwordErrors.confirmPassword" class="form-error">{{ passwordErrors.confirmPassword }}</p>
            </a-form-item>
            <a-button long :loading="savingPassword" @click="changePassword">{{ t('profile.updatePassword') }}</a-button>
          </a-form>

          <div class="security-list">
            <div class="security-item">
              <div>
                <strong>{{ t('profile.emailBound') }}</strong>
                <span>{{ auth.profile?.email || t('profile.emailUnbound') }}</span>
              </div>
              <StatusChip :label="auth.profile?.email ? t('common.ready') : t('common.disabled')" :tone="auth.profile?.email ? 'success' : 'neutral'" />
            </div>
            <div class="security-item">
              <div>
                <strong>{{ t('profile.deviceManagement') }}</strong>
                <span>{{ t('profile.deviceNotice') }}</span>
              </div>
              <StatusChip :label="t('common.ready')" tone="primary" />
            </div>
          </div>

          <div class="danger-zone">
            <div>
              <strong>{{ t('profile.dangerZone') }}</strong>
              <span>{{ t('profile.logoutCopy') }}</span>
            </div>
            <button class="danger-zone__button" type="button" :disabled="loggingOut" @click="confirmOpen = true">
              {{ t('common.logout') }}
            </button>
          </div>
        </article>
      </section>
    </a-spin>

    <ConfirmDialog
      v-model:open="confirmOpen"
      :title="t('userMenu.logoutTitle')"
      :description="t('userMenu.logoutDescription')"
      :cancel-label="t('common.cancel')"
      :confirm-label="t('common.logout')"
      :loading-label="t('common.loading')"
      :loading="loggingOut"
      @confirm="logout"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRouter } from 'vue-router';
import ConfirmDialog from '@/components/common/ConfirmDialog.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import StatusChip from '@/components/common/StatusChip.vue';
import { useAuthStore } from '@/stores/auth';

type ProfileTab = 'account' | 'learning' | 'preferences' | 'notifications';

const auth = useAuthStore();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const savingProfile = ref(false);
const savingPassword = ref(false);
const loggingOut = ref(false);
const confirmOpen = ref(false);
const activeTab = ref<ProfileTab>('account');
const profileForm = reactive({ displayName: '', email: '' });
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const passwordErrors = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });

const tabs = computed<Array<{ key: ProfileTab; label: string }>>(() => [
  { key: 'account', label: t('profile.tabAccount') },
  { key: 'learning', label: t('profile.tabLearning') },
  { key: 'preferences', label: t('profile.tabPreferences') },
  { key: 'notifications', label: t('profile.tabNotifications') }
]);

const roleText = computed(() => auth.profile?.roles.map((role) => t(`role.${role}`)).join(', ') || '-');
const displayName = computed(() => auth.profile?.displayName || auth.profile?.account || t('shell.studentFallback'));
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase());

function syncForm() {
  profileForm.displayName = auth.profile?.displayName || '';
  profileForm.email = auth.profile?.email || '';
}

async function loadProfile() {
  loading.value = true;
  error.value = '';
  try {
    await auth.loadProfile(true);
    syncForm();
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('profile.loadFailed');
  } finally {
    loading.value = false;
  }
}

async function saveProfile() {
  if (!profileForm.displayName.trim()) {
    Message.warning(t('profile.displayNameRequired'));
    return;
  }
  savingProfile.value = true;
  try {
    await auth.updateProfile({
      displayName: profileForm.displayName.trim(),
      email: profileForm.email.trim() || undefined
    });
    syncForm();
    Message.success(t('profile.profileUpdated'));
  } catch (err) {
    Message.error(err instanceof Error ? err.message : t('profile.profileUpdateFailed'));
  } finally {
    savingProfile.value = false;
  }
}

function validatePassword() {
  passwordErrors.currentPassword = passwordForm.currentPassword ? '' : t('profile.currentPasswordRequired');
  passwordErrors.newPassword = passwordForm.newPassword.length >= 6 ? '' : t('profile.passwordTooShort');
  passwordErrors.confirmPassword = passwordForm.newPassword === passwordForm.confirmPassword ? '' : t('profile.passwordMismatch');
  return !passwordErrors.currentPassword && !passwordErrors.newPassword && !passwordErrors.confirmPassword;
}

async function changePassword() {
  if (!validatePassword()) return;
  savingPassword.value = true;
  try {
    await auth.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    });
    passwordForm.currentPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirmPassword = '';
    Message.success(t('profile.passwordUpdated'));
  } catch (err) {
    Message.error(err instanceof Error ? err.message : t('profile.passwordUpdateFailed'));
  } finally {
    savingPassword.value = false;
  }
}

async function logout() {
  loggingOut.value = true;
  try {
    await auth.logout();
    await router.replace('/login');
  } finally {
    loggingOut.value = false;
    confirmOpen.value = false;
  }
}

onMounted(loadProfile);
</script>
