<template>
  <section class="view-stack">
    <a-alert v-if="error" type="error" show-icon :content="error" />
    <section class="role-grid">
      <a-card v-for="role in roleCards" :key="role.role" :bordered="false">
        <div class="role-card-head">
          <a-tag :color="role.color">{{ t(`role.${role.role}`) }}</a-tag>
          <strong>{{ role.label }}</strong>
        </div>
        <p>{{ role.description }}</p>
      </a-card>
    </section>

    <a-card :title="t('roles.matrix')" :bordered="false">
      <a-table :data="matrix" :pagination="false" row-key="permission">
        <template #columns>
          <a-table-column :title="t('roles.permission')" data-index="permission" />
          <a-table-column :title="t('role.STUDENT')" :width="130">
            <template #cell="{ record }">
              <a-tag :color="record.STUDENT ? 'green' : 'gray'">{{ record.STUDENT ? t('common.yes') : t('common.no') }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('role.TEACHER')" :width="130">
            <template #cell="{ record }">
              <a-tag :color="record.TEACHER ? 'green' : 'gray'">{{ record.TEACHER ? t('common.yes') : t('common.no') }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('role.ADMIN')" :width="130">
            <template #cell="{ record }">
              <a-tag :color="record.ADMIN ? 'green' : 'gray'">{{ record.ADMIN ? t('common.yes') : t('common.no') }}</a-tag>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api, type Role, type RoleResponse } from '@aioj/api-client';

const { t } = useI18n();
const roles = ref<RoleResponse[]>([]);
const error = ref('');

const descriptions: Record<Role, { color: string; fallbackKey: string; descriptionKey: string }> = {
  STUDENT: {
    color: 'arcoblue',
    fallbackKey: 'role.STUDENT',
    descriptionKey: 'roles.studentDescription'
  },
  TEACHER: {
    color: 'purple',
    fallbackKey: 'role.TEACHER',
    descriptionKey: 'roles.teacherDescription'
  },
  ADMIN: {
    color: 'red',
    fallbackKey: 'role.ADMIN',
    descriptionKey: 'roles.adminDescription'
  }
};

const matrix = computed(() => [
  { permission: t('roles.solveSubmit'), STUDENT: true, TEACHER: true, ADMIN: true },
  { permission: t('roles.useAiChat'), STUDENT: true, TEACHER: true, ADMIN: true },
  { permission: t('roles.reviewDrafts'), STUDENT: false, TEACHER: true, ADMIN: true },
  { permission: t('roles.editProblems'), STUDENT: false, TEACHER: true, ADMIN: true },
  { permission: t('roles.manageUsers'), STUDENT: false, TEACHER: false, ADMIN: true },
  { permission: t('roles.disableAccounts'), STUDENT: false, TEACHER: false, ADMIN: true }
]);

const roleCards = computed(() => {
  const labels = new Map(roles.value.map((role) => [role.role, role.label]));
  return (['STUDENT', 'TEACHER', 'ADMIN'] as Role[]).map((role) => ({
    role,
    label: labels.get(role) || t(descriptions[role].fallbackKey),
    color: descriptions[role].color,
    description: t(descriptions[role].descriptionKey)
  }));
});

onMounted(async () => {
  try {
    roles.value = await api.roles();
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('roles.loadFailed');
  }
});
</script>
