import { defineStore } from 'pinia';
import { api, authStore, type Role, type TokenResponse, type UserProfileResponse } from '@aioj/api-client';

interface AuthState {
  profile: UserProfileResponse | null;
  loading: boolean;
}

function profileFromToken(tokens: TokenResponse): UserProfileResponse {
  return {
    userId: tokens.userId,
    account: tokens.account,
    displayName: tokens.displayName,
    roles: tokens.roles
  };
}

export const useAuthStore = defineStore('admin-auth', {
  state: (): AuthState => ({
    profile: authStore.user ? profileFromToken(authStore.user) : null,
    loading: false
  }),
  getters: {
    isAuthenticated: () => Boolean(authStore.accessToken),
    isAdmin: (state) => Boolean(state.profile?.roles.includes('ADMIN')),
    displayName: (state) => state.profile?.displayName || authStore.user?.displayName || ''
  },
  actions: {
    async login(account: string, password: string) {
      const tokens = await api.login(account, password);
      authStore.save(tokens);
      this.profile = profileFromToken(tokens);
      return tokens;
    },
    async loadProfile(force = false) {
      if (!authStore.accessToken) {
        this.clearLocal();
        throw new Error('Login required');
      }
      if (this.profile && !force) return this.profile;
      this.loading = true;
      try {
        this.profile = await api.me();
        return this.profile;
      } catch (error) {
        this.clearLocal();
        throw error;
      } finally {
        this.loading = false;
      }
    },
    hasRole(role: Role) {
      return Boolean(this.profile?.roles.includes(role));
    },
    async logout() {
      try {
        if (authStore.accessToken) {
          await api.logout();
        }
      } finally {
        this.clearLocal();
      }
    },
    clearLocal() {
      authStore.clear();
      this.profile = null;
    }
  }
});
