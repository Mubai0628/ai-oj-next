<template>
  <section class="view-stack">
    <a-card :bordered="false" class="filter-card">
      <div class="toolbar-row">
        <a-space wrap>
          <a-input-search v-model="filters.keyword" :placeholder="t('adminUsers.search')" allow-clear @search="loadUsers" />
          <a-select v-model="filters.role" :placeholder="t('common.roles')" allow-clear class="filter-control" @change="loadUsers">
            <a-option value="">{{ t('adminUsers.allRoles') }}</a-option>
            <a-option v-for="role in roles" :key="role.role" :value="role.role">{{ roleLabel(role.role, role.label) }}</a-option>
          </a-select>
          <a-select v-model="filters.enabled" :placeholder="t('common.status')" allow-clear class="filter-control" @change="loadUsers">
            <a-option value="">{{ t('adminUsers.allStatuses') }}</a-option>
            <a-option :value="true">{{ t('common.enabled') }}</a-option>
            <a-option :value="false">{{ t('common.disabled') }}</a-option>
          </a-select>
        </a-space>
        <a-space wrap>
          <a-button @click="loadUsers">{{ t('common.refresh') }}</a-button>
          <a-button type="primary" @click="openCreate">{{ t('adminUsers.create') }}</a-button>
        </a-space>
      </div>
    </a-card>

    <a-alert v-if="error" type="error" show-icon :content="error" />
    <a-card :bordered="false">
      <a-table :data="users" :loading="loading" :pagination="false" row-key="userId">
        <template #columns>
          <a-table-column :title="t('common.account')" data-index="account" :width="160" />
          <a-table-column :title="t('common.displayName')" data-index="displayName" />
          <a-table-column :title="t('common.email')" data-index="email" />
          <a-table-column :title="t('common.roles')" :width="230">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag v-for="role in record.roles" :key="role" :color="role === 'ADMIN' ? 'red' : 'arcoblue'">
                  {{ t(`role.${role}`) }}
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.status')" :width="120">
            <template #cell="{ record }">
              <a-tag :color="record.enabled ? 'green' : 'gray'">{{ record.enabled ? t('common.enabled') : t('common.disabled') }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.actions')" :width="230">
            <template #cell="{ record }">
              <a-space>
                <a-button size="small" @click="openEdit(record)">{{ t('common.edit') }}</a-button>
                <a-popconfirm :content="t('adminUsers.disableConfirm')" @ok="disableUser(record)">
                  <a-button size="small" status="danger" :disabled="!record.enabled">{{ t('common.disable') }}</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && users.length === 0" :description="t('adminUsers.empty')" />
    </a-card>

    <a-modal v-model:visible="modalVisible" :title="editingUser ? t('adminUsers.editModal') : t('adminUsers.createModal')" :ok-loading="saving" @ok="saveUser">
      <a-form :model="form" layout="vertical">
        <a-alert
          v-if="roleWarning"
          type="warning"
          show-icon
          class="form-alert"
          :content="roleWarning"
        />
        <a-form-item
          v-if="!editingUser"
          :label="t('common.account')"
          :validate-status="fieldError('account') ? 'error' : undefined"
          :help="fieldError('account') || undefined"
        >
          <a-input v-model="form.account" />
        </a-form-item>
        <a-form-item
          v-if="!editingUser"
          :label="t('common.password')"
          :validate-status="fieldError('password') ? 'error' : undefined"
          :help="fieldError('password') || undefined"
        >
          <a-input-password v-model="form.password" />
        </a-form-item>
        <a-form-item
          :label="t('common.displayName')"
          :validate-status="fieldError('displayName') ? 'error' : undefined"
          :help="fieldError('displayName') || undefined"
        >
          <a-input v-model="form.displayName" />
        </a-form-item>
        <a-form-item
          :label="t('common.email')"
          :validate-status="fieldError('email') ? 'error' : undefined"
          :help="fieldError('email') || undefined"
        >
          <a-input v-model="form.email" />
        </a-form-item>
        <a-form-item
          :label="t('adminUsers.baseIdentity')"
          :validate-status="fieldError('roles') ? 'error' : undefined"
          :help="fieldError('roles') || undefined"
        >
          <a-radio-group v-model="form.baseRole" type="button">
            <a-radio value="STUDENT">{{ t('auth.studentRole') }}</a-radio>
            <a-radio value="TEACHER">{{ t('auth.teacherRole') }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item :label="t('adminUsers.adminPermission')">
          <div class="role-switch-row">
            <a-switch v-model="form.adminAccess" />
            <span>{{ t('adminUsers.adminPermissionCopy') }}</span>
          </div>
        </a-form-item>
        <a-form-item :label="t('common.enabled')">
          <a-switch v-model="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type AdminUserResponse, type Role, type RoleResponse } from '@aioj/api-client';

const { t } = useI18n();
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const users = ref<AdminUserResponse[]>([]);
const roles = ref<RoleResponse[]>([]);
const modalVisible = ref(false);
const editingUser = ref<AdminUserResponse | null>(null);
const roleWarning = ref('');
const fieldErrors = ref<Record<string, string>>({});
type BaseRole = Extract<Role, 'STUDENT' | 'TEACHER'>;
const filters = reactive<{ keyword: string; role: Role | ''; enabled: boolean | '' }>({
  keyword: '',
  role: '',
  enabled: ''
});
const form = reactive({
  account: '',
  password: '',
  displayName: '',
  email: '',
  baseRole: 'STUDENT' as BaseRole | '',
  adminAccess: false,
  enabled: true
});

async function loadRoles() {
  try {
    roles.value = await api.roles();
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('adminUsers.loadRolesFailed'));
  }
}

async function loadUsers() {
  loading.value = true;
  error.value = '';
  try {
    users.value = (await api.users({ ...filters, page: 1, pageSize: 50 })).records;
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('adminUsers.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function fieldError(path: string) {
  return fieldErrors.value[path];
}

function roleLabel(role: Role, fallback?: string) {
  return t(`role.${role}`) || fallback || role;
}

function resetForm() {
  form.account = '';
  form.password = '';
  form.displayName = '';
  form.email = '';
  form.baseRole = 'STUDENT';
  form.adminAccess = false;
  form.enabled = true;
  roleWarning.value = '';
  fieldErrors.value = {};
}

function openCreate() {
  editingUser.value = null;
  resetForm();
  modalVisible.value = true;
}

function openEdit(user: AdminUserResponse) {
  editingUser.value = user;
  const hasStudent = user.roles.includes('STUDENT');
  const hasTeacher = user.roles.includes('TEACHER');
  form.account = user.account;
  form.password = '';
  form.displayName = user.displayName;
  form.email = user.email || '';
  form.baseRole = hasStudent && hasTeacher ? '' : hasTeacher ? 'TEACHER' : 'STUDENT';
  form.adminAccess = user.roles.includes('ADMIN');
  form.enabled = user.enabled;
  roleWarning.value = hasStudent && hasTeacher ? t('adminUsers.roleConflictWarning') : '';
  modalVisible.value = true;
}

function assembledRoles(): Role[] {
  const result: Role[] = form.baseRole ? [form.baseRole] : [];
  if (form.adminAccess) result.push('ADMIN');
  return result;
}

function validateRoles(rolesToSave: Role[]) {
  if (rolesToSave.includes('STUDENT') && rolesToSave.includes('TEACHER')) {
    return t('adminUsers.roleConflict');
  }
  if (!rolesToSave.includes('STUDENT') && !rolesToSave.includes('TEACHER')) {
    return t('adminUsers.baseIdentityRequired');
  }
  return '';
}

async function saveUser() {
  fieldErrors.value = {};
  const rolesToSave = assembledRoles();
  const roleError = validateRoles(rolesToSave);
  if (roleError) {
    Message.error(roleError);
    return;
  }
  saving.value = true;
  try {
    if (editingUser.value) {
      await api.updateUser(editingUser.value.userId, {
        displayName: form.displayName.trim(),
        email: form.email.trim() || undefined,
        roles: rolesToSave,
        enabled: form.enabled
      });
      Message.success(t('adminUsers.userUpdated'));
    } else {
      await api.createUser({
        account: form.account.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        email: form.email.trim() || undefined,
        roles: rolesToSave,
        enabled: form.enabled
      });
      Message.success(t('adminUsers.userCreated'));
    }
    fieldErrors.value = {};
    modalVisible.value = false;
    await loadUsers();
  } catch (caught) {
    if (caught instanceof ApiError) {
      fieldErrors.value = caught.details ?? {};
      Message.error(caught.userMessage);
    } else {
      Message.error(caught instanceof Error ? caught.message : t('adminUsers.saveFailed'));
    }
  } finally {
    saving.value = false;
  }
}

async function disableUser(user: AdminUserResponse) {
  try {
    await api.updateUser(user.userId, {
      displayName: user.displayName,
      email: user.email,
      roles: normalizeExistingRoles(user.roles),
      enabled: false
    });
    Message.success(t('adminUsers.userDisabled'));
    await loadUsers();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('adminUsers.disableFailed')));
  }
}

function normalizeExistingRoles(userRoles: Role[]) {
  const hasTeacher = userRoles.includes('TEACHER');
  const base: BaseRole = hasTeacher ? 'TEACHER' : 'STUDENT';
  const normalized: Role[] = [base];
  if (userRoles.includes('ADMIN')) normalized.push('ADMIN');
  return normalized;
}

onMounted(async () => {
  await loadRoles();
  await loadUsers();
});
</script>
