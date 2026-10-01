import { create } from "zustand";
import type { Me, Role } from "@/types";
import { authApi } from "@/api/auth";

interface AuthState {
  user: Me | null;
  initialized: boolean;
  login: (email: string, password: string) => Promise<void>;
  loadMe: () => Promise<void>;
  logout: () => Promise<void>;
  hasAnyRole: (...roles: Role[]) => boolean;
  isManager: () => boolean;
}

/**
 * 인증 상태. 인증 정보 자체는 서버 세션(HttpOnly 쿠키)에만 있고 브라우저 저장소에 두지 않는다.
 * 로그인·로그아웃 시 서버가 CSRF 토큰을 폐기하므로 직후 새 토큰을 받는다.
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  initialized: false,

  login: async (email, password) => {
    await authApi.csrf();
    const user = await authApi.login(email, password);
    await authApi.csrf();
    set({ user, initialized: true });
  },

  loadMe: async () => {
    try {
      await authApi.csrf();
      const user = await authApi.me();
      set({ user, initialized: true });
    } catch {
      set({ user: null, initialized: true });
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      set({ user: null });
      await authApi.csrf().catch(() => undefined);
    }
  },

  hasAnyRole: (...roles) => {
    const user = get().user;
    if (!user) return false;
    return user.roles.some((r) => roles.includes(r));
  },

  isManager: () => {
    const user = get().user;
    if (!user) return false;
    return user.roles.some((r) => r === "SYSTEM_ADMIN" || r === "HR_ADMIN");
  },
}));
