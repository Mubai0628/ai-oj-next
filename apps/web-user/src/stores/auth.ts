import { defineStore } from 'pinia';
import { api, authStore, type UserProfileResponse } from '@aioj/api-client';

interface AuthState {
  profile: UserProfileResponse | null;
  loading: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    profile: null,
    loading: false
  }),
  getters: {
    isAuthenticated: () => Boolean(authStore.accessToken)
  },
  actions: {
    async login(account: string, password: string) {
      const tokens = await api.login(account, password);
      authStore.save(tokens);
      this.profile = {
        userId: tokens.userId,
        account: tokens.account,
        displayName: tokens.displayName,
        roles: tokens.roles
      };
      return tokens;
    },
    async register(payload: { account: string; password: string; displayName: string; email?: string }) {
      const tokens = await api.register(payload);
      authStore.save(tokens);
      this.profile = {
        userId: tokens.userId,
        account: tokens.account,
        displayName: tokens.displayName,
        email: payload.email,
        roles: tokens.roles
      };
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
    async updateProfile(payload: { displayName: string; email?: string }) {
      this.profile = await api.updateMe(payload);
      return this.profile;
    },
    async changePassword(payload: { currentPassword: string; newPassword: string }) {
      return api.changePassword(payload);
    },
    async logout() {
      try {
        await api.logout();
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
