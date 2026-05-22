import { onBeforeUnmount, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

export function useAdminSessionGuard() {
  const auth = useAuthStore();
  const route = useRoute();
  const router = useRouter();

  const handleExpired = () => {
    if (route.name === 'login') return;
    const redirect = route.fullPath || '/dashboard';
    auth.clearLocal();
    void router.replace({ name: 'login', query: { expired: '1', redirect } });
  };

  onMounted(() => {
    window.addEventListener('aioj:auth-expired', handleExpired);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('aioj:auth-expired', handleExpired);
  });
}
