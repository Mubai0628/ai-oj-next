import { defineStore } from 'pinia';
import { api, authStore, type UserProfileResponse } from '@aioj/api-client';

interface AuthState {
  profile: UserProfileResponse | null;
  profileFetchedAt: number | null;
  loading: boolean;
  authTick: number;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    profile: null,
    profileFetchedAt: null,
    loading: false,
    authTick: 0
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
      this.profileFetchedAt = Date.now();
      this.authTick = this.authTick + 1;
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
      this.profileFetchedAt = Date.now();
      this.authTick = this.authTick + 1;
      return tokens;
    },
    async loadProfile(force = false) {
      if (!authStore.accessToken) {
        this.clearLocal();
        throw new Error('Login required');
      }
      const STALE_MS = 30_000;
      const fresh = this.profile && this.profileFetchedAt && (Date.now() - this.profileFetchedAt) < STALE_MS;
      if (fresh && !force) return this.profile;
      this.loading = true;
      try {
        this.profile = await api.me();
        this.profileFetchedAt = Date.now();
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
      this.profileFetchedAt = null;
      this.authTick = this.authTick + 1;
    }
  }
});
