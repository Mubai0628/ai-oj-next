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
    path: '/',
    component: () => import('@/layouts/AppShell.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
      { path: 'problems', name: 'problems', component: () => import('@/views/ProblemsView.vue') },
      { path: 'problems/:id', name: 'problem-detail', component: () => import('@/views/ProblemDetailView.vue'), props: true },
      { path: 'submissions', name: 'submissions', component: () => import('@/views/SubmissionsView.vue') },
      { path: 'ai-chat', name: 'ai-chat', component: () => import('@/views/AiChatView.vue') },
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    if (authStore.accessToken && (to.name === 'login' || to.name === 'register')) {
      return { name: 'home' };
    }
    return true;
  }

  if (!authStore.accessToken) {
    auth.clearLocal();
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  try {
    await auth.loadProfile();
    return true;
  } catch {
    return { name: 'login', query: { redirect: to.fullPath, expired: '1' } };
  }
});

export default router;
