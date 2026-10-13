import { create } from "zustand";
import { roleIncludes, type Me, type Role } from "@/types";
import axios from "axios";
import { authApi } from "@/api/auth";
import { extractErrorMessage } from "@/api/client";
import { queryClient } from "@/lib/queryClient";

interface AuthState {
  user: Me | null;
  initialized: boolean;
  /** 앱을 열 때 로그인 확인이 401 이 아닌 오류(서버 오류·네트워크)로 실패했으면 안내 문구. 로그인 화면으로 보내지 않고 다시 시도하게 한다 */
  loadError: string | null;
  login: (email: string, password: string, rememberMe?: boolean) => Promise<void>;
  loadMe: () => Promise<void>;
  /** 로그인한 채로 내 정보(이름·부서·권한)만 다시 받는다. 실패하면 그대로 둔다(세션이 끊겼으면 다음 요청이 로그인 화면으로 보냄) */
  refreshMe: () => Promise<void>;
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
  loadError: null,

  login: async (email, password, rememberMe = false) => {
    await authApi.csrf();
    const user = await authApi.login(email, password, rememberMe);
    await authApi.csrf();
    set({ user, initialized: true, loadError: null });
  },

  loadMe: async () => {
    try {
      await authApi.csrf();
      const user = await authApi.me();
      set({ user, initialized: true, loadError: null });
    } catch (err) {
      const sessionLost = axios.isAxiosError(err) && err.response?.status === 401;
      set({
        user: null,
        initialized: true,
        loadError: sessionLost ? null : extractErrorMessage(err, "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
      });
    }
  },

  refreshMe: async () => {
    if (!get().user) return;
    try {
      const user = await authApi.me();
      set({ user });
    } catch {
      // 그대로 둔다
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
    return roles.some((r) => roleIncludes(user.role, r));
  },

  isManager: () => {
    const user = get().user;
    if (!user) return false;
    return roleIncludes(user.role, "HR_ADMIN");
  },
}));

// 로그인한 사람이 바뀌면(로그아웃, 다른 계정 로그인, 세션이 끊겨 로그인 화면으로) 앞사람의 화면 데이터를 지운다.
// 그대로 두면 공용 PC에서 다음 사람에게 30초 동안 앞사람의 잔여 연차·결재 대기·알림이 보인다.
useAuthStore.subscribe((state, prev) => {
  if (state.user?.id !== prev.user?.id) queryClient.clear();
});
