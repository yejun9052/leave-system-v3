import axios, { AxiosError } from "axios";
import { useMaintenanceStore } from "@/store/maintenance";

/**
 * 백엔드 표준 응답 형태.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error: { code: string; message: string; details?: unknown } | null;
}

/**
 * 인증은 서버 세션(HttpOnly 쿠키)으로 처리되어 브라우저가 자동 전송한다.
 * CSRF: 서버가 내려준 XSRF-TOKEN 쿠키 값을 axios 가 X-XSRF-TOKEN 헤더로 자동 첨부(같은 출처 요청).
 */
export const api = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

// 앱 초기 로그인 확인(/auth/me)과 로그인 시도 자체의 401 은 호출부에서 처리
const SKIP_REDIRECT_URLS = ["/auth/me", "/auth/login"];

/**
 * 세션이 없어서(만료·로그아웃·퇴사 처리) 받은 401 인지. 서버는 이때 오류 코드 UNAUTHORIZED 를 준다.
 * 그 외 코드의 401(예: 로그인 실패)은 입력 오류라 로그인 페이지로 보내지 않는다.
 */
function isSessionLost(error: AxiosError): boolean {
  if (error.response?.status !== 401) return false;
  const code = (error.response.data as ApiEnvelope<unknown> | undefined)?.error?.code;
  return !code || code === "UNAUTHORIZED";
}

api.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const url = error.config?.url ?? "";
    // 시스템 점검(데이터 복원 중) → 화면 전체에 점검 안내(MaintenanceOverlay)
    if (
      error.response?.status === 503 &&
      (error.response.data as ApiEnvelope<unknown> | undefined)?.error?.code === "MAINTENANCE"
    ) {
      useMaintenanceStore.getState().setActive(true);
    }
    if (
      isSessionLost(error) &&
      !SKIP_REDIRECT_URLS.includes(url) &&
      window.location.pathname !== "/login"
    ) {
      // 세션 만료·강제 종료 → 로그인 페이지로
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

/** 응답 envelope 에서 data 만 추출하고, 오류 메시지를 표준화한다. */
export function unwrap<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  return promise.then((res) => res.data.data);
}

/** axios 오류에서 서버 오류 코드(ErrorCode 이름)를 뽑아낸다. 없으면 null. */
export function extractErrorCode(err: unknown): string | null {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiEnvelope<unknown> | undefined;
    return body?.error?.code ?? null;
  }
  return null;
}

/** axios 오류에서 사용자에게 보여줄 메시지를 뽑아낸다. */
export function extractErrorMessage(err: unknown, fallback = "요청 처리 중 오류가 발생했습니다."): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiEnvelope<unknown> | undefined;
    if (body?.error?.message) return body.error.message;
  }
  return fallback;
}
