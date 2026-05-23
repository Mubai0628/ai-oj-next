import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { authStore } from '@aioj/api-client';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/blocked',
    name: 'blocked',
    component: () => import('@/views/BlockedView.vue'),
    meta: { requiresAuth: true, allowNonAdmin: true }
  },
  {
    path: '/',
    component: () => import('@/views/AppShell.vue'),
    meta: { requiresAuth: true, adminOnly: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
      { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue') },
      { path: 'roles', name: 'roles', component: () => import('@/views/RolesView.vue') },
      { path: 'problems', name: 'problems', component: () => import('@/views/ProblemsView.vue') },
      { path: 'ai-drafts', name: 'ai-drafts', component: () => import('@/views/AiDraftsView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

function handleAuthExpired() {
  const auth = useAuthStore();
  const currentRoute = router.currentRoute.value;

  auth.clearLocal();
  if (currentRoute.name === 'login' || currentRoute.name === 'register') return;

  const redirect = currentRoute.fullPath || '/dashboard';
  void router.replace({ name: 'login', query: { expired: '1', redirect } });
}

window.addEventListener('aioj:auth-expired', handleAuthExpired);
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    window.removeEventListener('aioj:auth-expired', handleAuthExpired);
  });
}

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  if (to.meta.public) {
    if (!authStore.accessToken) return true;
    try {
      await auth.loadProfile();
      return auth.isAdmin ? { name: 'dashboard' } : { name: 'blocked' };
    } catch {
      return true;
    }
  }

  if (!authStore.accessToken) {
    auth.clearLocal();
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  try {
    await auth.loadProfile();
  } catch {
    return { name: 'login', query: { redirect: to.fullPath, expired: '1' } };
  }

  if (to.name === 'blocked') {
    return auth.isAdmin ? { name: 'dashboard' } : true;
  }

  if (to.meta.adminOnly && !auth.isAdmin) {
    return { name: 'blocked' };
  }

  return true;
});

export default router;
