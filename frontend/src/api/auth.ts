import { api, unwrap } from "./client";
import type { Me } from "@/types";

export const authApi = {
  /** CSRF 토큰 쿠키(XSRF-TOKEN) 발급. 앱 시작·로그인·로그아웃 직후 호출. */
  csrf: () => unwrap<null>(api.get("/auth/csrf")),

  /** 성공 시 서버가 세션 쿠키를 설정하고 사용자 정보를 돌려준다. */
  login: (email: string, password: string) =>
    unwrap<Me>(api.post("/auth/login", { email, password })),

  logout: () => unwrap<null>(api.post("/auth/logout")),

  me: () => unwrap<Me>(api.get("/auth/me")),

  /** 비밀번호 찾기: 등록 여부와 관계없이 항상 같은 응답 */
  requestPasswordReset: (email: string) =>
    unwrap<null>(api.post("/auth/password-reset/request", { email })),

  confirmPasswordReset: (token: string, newPassword: string) =>
    unwrap<null>(api.post("/auth/password-reset/confirm", { token, newPassword })),
};
