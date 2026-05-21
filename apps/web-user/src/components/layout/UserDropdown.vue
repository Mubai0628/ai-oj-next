<template>
  <div ref="root" class="user-dropdown">
    <button
      class="user-dropdown__trigger"
      type="button"
      :class="{ active: open }"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="toggle"
    >
      <img v-if="user.avatar" class="user-dropdown__avatar" :src="user.avatar" :alt="user.name" />
      <span v-else class="user-dropdown__avatar">{{ initial }}</span>
      <span class="user-dropdown__name">{{ user.name }}</span>
      <span class="user-dropdown__chevron" />
    </button>

    <Transition name="user-menu">
      <section v-if="open" class="user-dropdown__menu" role="menu">
        <header class="user-dropdown__header">
          <img v-if="user.avatar" class="user-dropdown__avatar user-dropdown__avatar--large" :src="user.avatar" :alt="user.name" />
          <span v-else class="user-dropdown__avatar user-dropdown__avatar--large">{{ initial }}</span>
          <div>
            <strong>{{ user.name }}</strong>
            <span>{{ user.role || t('shell.studentFallback') }}</span>
          </div>
        </header>

        <div class="user-dropdown__items">
          <button
            v-for="item in menuItems"
            :key="item.to"
            class="user-dropdown__item"
            :class="{ active: isActive(item.to) }"
            type="button"
            role="menuitem"
            @click="go(item.to)"
          >
            <span class="user-dropdown__item-icon">{{ item.icon }}</span>
            {{ item.label }}
          </button>
        </div>

        <button class="user-dropdown__item user-dropdown__item--danger" type="button" role="menuitem" @click="confirmLogout">
          <span class="user-dropdown__item-icon">!</span>
          {{ t('common.logout') }}
        </button>
      </section>
    </Transition>

    <ConfirmDialog
      v-model:open="confirmOpen"
      :title="t('userMenu.logoutTitle')"
      :description="t('userMenu.logoutDescription')"
      :cancel-label="t('common.cancel')"
      :confirm-label="t('common.logout')"
      :loading-label="t('common.loading')"
      :loading="loggingOut"
      @confirm="$emit('logout')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import ConfirmDialog from '@/components/common/ConfirmDialog.vue';

export interface UserMenuUser {
  id?: string | number;
  name: string;
  role?: string;
  avatar?: string;
  email?: string;
}

const props = withDefaults(defineProps<{
  user: UserMenuUser;
  loggingOut?: boolean;
}>(), {
  loggingOut: false
});

defineEmits<{ logout: [] }>();

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const root = ref<HTMLElement | null>(null);
const open = ref(false);
const confirmOpen = ref(false);

const menuItems = computed(() => [
  { to: '/profile', label: t('userMenu.profile'), icon: 'U' },
  { to: '/submissions', label: t('userMenu.submissions'), icon: 'S' },
  { to: '/profile', label: t('userMenu.settings'), icon: 'C' }
]);

const initial = computed(() => props.user.name.trim().slice(0, 1).toUpperCase() || 'U');

function toggle() {
  open.value = !open.value;
}

function close() {
  open.value = false;
}

function isActive(path: string) {
  return route.path === path;
}

async function go(path: string) {
  close();
  if (route.path !== path) await router.push(path);
}

function confirmLogout() {
  close();
  confirmOpen.value = true;
}

function onDocumentClick(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) close();
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') close();
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick);
  window.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick);
  window.removeEventListener('keydown', onKeydown);
});

watch(() => route.fullPath, close);
</script>

<style scoped>
.user-dropdown {
  position: relative;
  display: inline-flex;
}

.user-dropdown__trigger {
  min-height: 40px;
  max-width: 190px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 5px;
  border: 1px solid var(--color-border-soft);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.user-dropdown__trigger:hover,
.user-dropdown__trigger.active {
  border-color: rgba(37, 99, 235, 0.34);
  background: #f8fbff;
  color: var(--color-primary);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.user-dropdown__avatar {
  width: 30px;
  height: 30px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 30px;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(145deg, #dbeafe, #e0f2fe);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 900;
  object-fit: cover;
}

.user-dropdown__avatar--large {
  width: 42px;
  height: 42px;
  flex-basis: 42px;
  font-size: 17px;
}

.user-dropdown__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-dropdown__chevron {
  width: 7px;
  height: 7px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: translateY(-2px) rotate(45deg);
}

.user-dropdown__menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 50;
  width: 248px;
  padding: 10px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 18px;
  background: var(--color-surface);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.14);
}

.user-dropdown__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff, #eff6ff);
}

.user-dropdown__header strong {
  display: block;
  overflow: hidden;
  color: var(--color-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-dropdown__header span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.user-dropdown__items {
  display: grid;
  gap: 4px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-soft);
}

.user-dropdown__item {
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.user-dropdown__item:hover,
.user-dropdown__item.active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.user-dropdown__item--danger {
  margin-top: 8px;
  color: var(--color-danger);
}

.user-dropdown__item--danger:hover {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.user-dropdown__item-icon {
  width: 24px;
  height: 24px;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface-soft);
  font-size: 11px;
  font-weight: 900;
}

.user-menu-enter-active,
.user-menu-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.user-menu-enter-from,
.user-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 560px) {
  .user-dropdown__trigger {
    max-width: 150px;
  }

  .user-dropdown__menu {
    right: -8px;
    width: min(248px, calc(100vw - 32px));
  }
}
</style>
