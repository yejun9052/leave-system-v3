import { create } from "zustand";
import type { Me, Role } from "@/types";
import { tokenStore } from "@/lib/tokenStore";
import { authApi } from "@/api/auth";

interface AuthState {
  user: Me | null;
  initialized: boolean;
  login: (email: string, password: string) => Promise<void>;
  loadMe: () => Promise<void>;
  logout: () => void;
  hasAnyRole: (...roles: Role[]) => boolean;
  isManager: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  initialized: false,

  login: async (email, password) => {
    const tokens = await authApi.login(email, password);
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    const user = await authApi.me();
    set({ user, initialized: true });
  },

  loadMe: async () => {
    if (!tokenStore.getAccess()) {
      set({ user: null, initialized: true });
      return;
    }
    try {
      const user = await authApi.me();
      set({ user, initialized: true });
    } catch {
      tokenStore.clear();
      set({ user: null, initialized: true });
    }
  },

  logout: () => {
    tokenStore.clear();
    set({ user: null });
  },

  hasAnyRole: (...roles) => {
    const user = get().user;
    if (!user) return false;
    return user.roles.some((r) => roles.includes(r));
  },

  isManager: () => {
    const user = get().user;
    if (!user) return false;
    return user.roles.some((r) => r === "SUPER_ADMIN" || r === "HR_ADMIN");
  },
}));
