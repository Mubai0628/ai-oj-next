<template>
  <aside class="student-sidebar">
    <router-link class="sidebar-brand" to="/">
      <span class="sidebar-logo">AI</span>
      <span class="brand-copy">
        <strong>AI-OJ</strong>
        <small>{{ subtitle }}</small>
      </span>
    </router-link>

    <nav class="sidebar-nav" :aria-label="navLabel">
      <router-link v-for="item in items" :key="item.to" :to="item.to" class="sidebar-link">
        <span class="sidebar-icon" aria-hidden="true">{{ item.icon }}</span>
        <span class="sidebar-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar-user">
      <span class="app-avatar">{{ initial }}</span>
      <span class="sidebar-user-copy">
        <strong>{{ displayName }}</strong>
        <small>{{ roleLabel }}</small>
      </span>
    </div>
  </aside>
</template>

<script setup lang="ts">
interface SidebarItem {
  to: string;
  label: string;
  icon: string;
}

defineProps<{
  items: SidebarItem[];
  subtitle: string;
  navLabel: string;
  displayName: string;
  roleLabel: string;
  initial: string;
}>();
</script>

<style scoped>
.sidebar-brand {
  min-height: 44px;
  display: grid;
  justify-items: center;
  gap: 6px;
  color: var(--color-text);
}

.sidebar-logo {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
}

.brand-copy {
  display: grid;
  gap: 1px;
  justify-items: center;
}

.brand-copy strong {
  font-size: 13px;
  line-height: 1.1;
}

.brand-copy small {
  color: var(--color-text-muted);
  font-size: 10px;
  line-height: 1.2;
}

.sidebar-nav {
  display: grid;
  gap: 8px;
  margin-top: 24px;
}

.sidebar-link {
  position: relative;
  min-height: 42px;
  display: grid;
  grid-template-columns: 22px 1fr;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.sidebar-link:hover {
  background: var(--color-surface-soft);
  color: var(--color-primary);
}

.sidebar-link.router-link-active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.sidebar-link.router-link-active::before {
  content: "";
  position: absolute;
  left: -12px;
  top: 11px;
  width: 3px;
  height: 20px;
  border-radius: 999px;
  background: var(--color-primary);
}

.sidebar-icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  color: currentColor;
  font-size: 12px;
  font-weight: 900;
}

.sidebar-user {
  margin-top: auto;
  display: grid;
  justify-items: center;
  gap: 7px;
  padding-top: 18px;
}

.sidebar-user-copy {
  display: grid;
  justify-items: center;
  gap: 1px;
  min-width: 0;
}

.sidebar-user-copy strong {
  max-width: 92px;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-user-copy small {
  color: var(--color-text-muted);
  font-size: 11px;
}

@media (max-width: 1024px) {
  .sidebar-brand {
    justify-items: center;
  }

  .brand-copy small,
  .sidebar-label,
  .sidebar-user-copy {
    display: none;
  }

  .sidebar-link {
    grid-template-columns: 1fr;
    justify-items: center;
    padding: 0;
  }

  .sidebar-link.router-link-active::before {
    left: -12px;
  }
}

@media (max-width: 768px) {
  .student-sidebar {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    gap: 12px;
  }

  .sidebar-brand {
    justify-items: start;
  }

  .brand-copy small,
  .sidebar-label {
    display: block;
  }

  .sidebar-nav {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 6px;
    margin-top: 0;
  }

  .sidebar-link {
    grid-template-columns: 1fr;
    justify-items: center;
    min-height: 38px;
    padding: 0 4px;
  }

  .sidebar-link.router-link-active::before,
  .sidebar-user {
    display: none;
  }

  .sidebar-label {
    font-size: 11px;
  }
}
</style>
