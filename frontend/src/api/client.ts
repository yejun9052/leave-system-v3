import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { tokenStore } from "@/lib/tokenStore";

/**
 * 백엔드 표준 응답 형태.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error: { code: string; message: string; details?: unknown } | null;
}

export const api = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccess();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// --- Refresh-token handling on 401 ---
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refresh = tokenStore.getRefresh();
  if (!refresh) return null;
  try {
    const res = await axios.post<ApiEnvelope<{ accessToken: string; refreshToken: string }>>(
      "/api/auth/refresh",
      { refreshToken: refresh },
    );
    const data = res.data.data;
    tokenStore.set(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true;
      if (!refreshing) refreshing = refreshAccessToken();
      const newToken = await refreshing;
      refreshing = null;
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
      // refresh 실패 → 로그인 페이지로
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

/** 응답 envelope 에서 data 만 추출하고, 오류 메시지를 표준화한다. */
export function unwrap<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  return promise.then((res) => res.data.data);
}

/** axios 오류에서 사용자에게 보여줄 메시지를 뽑아낸다. */
export function extractErrorMessage(err: unknown, fallback = "요청 처리 중 오류가 발생했습니다."): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiEnvelope<unknown> | undefined;
    if (body?.error?.message) return body.error.message;
  }
  return fallback;
}
