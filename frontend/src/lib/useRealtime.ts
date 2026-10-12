import { useEffect } from "react";
import axios from "axios";
import { useQueryClient } from "@tanstack/react-query";
import { authApi } from "@/api/auth";
import { CLIENT_ID } from "@/api/client";
import { useAuthStore } from "@/store/auth";

/** 신호가 몰려 오면 한 번에 묶어 다시 불러온다 */
const REFRESH_DELAY_MS = 400;
/** 연결을 서버가 거절했을 때(점검 중 등) 다시 시도하기까지 */
const RETRY_MS = 10_000;
/** 숨은 탭이 로그인을 유지하려고 서버에 가볍게 묻는 간격(세션 2시간보다 짧게) */
const HIDDEN_PING_MS = 20 * 60_000;

/**
 * 실시간 갱신(SSE, GET /api/realtime). 누가 데이터를 바꾸면 서버가 "바뀜" 신호만 보내고,
 * 이 탭은 지금 화면에 떠 있는 데이터만 다시 불러온다(TanStack Query invalidate → 보이는 쿼리만 다시 요청).
 *
 * - 내가 바꾼 신호(origin = 이 탭)는 무시한다. 저장한 화면이 이미 스스로 다시 불러왔다.
 * - 보이지 않는 탭은 연결을 끊는다(HTTP 로 운영할 때 탭이 많으면 연결 수 제한에 걸린다).
 *   대신 20분마다 로그인을 확인해 열어 둔 탭은 로그아웃되지 않게 하고, 다시 보이면 연결해 한 번 새로 불러온다.
 * - 연결이 끊겼다 붙으면(서버 재시작, 30분마다 다시 연결) 놓친 신호가 있을 수 있어 한 번 새로 불러온다.
 * - 로그인이 끊겨 연결이 거절되면 로그인 화면으로 보낸다.
 */
export function useRealtime(enabled: boolean) {
  const qc = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    let source: EventSource | null = null;
    let missed = false;
    let stopped = false;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let pingTimer: ReturnType<typeof setInterval> | undefined;

    const refresh = () => {
      clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => void qc.invalidateQueries(), REFRESH_DELAY_MS);
    };

    const close = () => {
      source?.close();
      source = null;
    };

    // 서버가 연결을 거절했다(로그아웃됨·점검 중 등): 로그인 상태를 확인하고 다시 연결
    const retryLater = () => {
      clearTimeout(retryTimer);
      retryTimer = setTimeout(async () => {
        if (stopped) return;
        try {
          await authApi.me();
          open();
        } catch (err) {
          if (axios.isAxiosError(err) && err.response?.status === 401) {
            useAuthStore.setState({ user: null }); // → 로그인 화면(돌아올 주소 기억)
          } else {
            retryLater();
          }
        }
      }, RETRY_MS);
    };

    const open = () => {
      if (source || stopped || document.visibilityState !== "visible") return;
      const es = new EventSource("/api/realtime");
      source = es;
      es.addEventListener("connected", () => {
        if (missed) {
          missed = false;
          refresh();
        }
      });
      es.addEventListener("change", (e) => {
        let origin = "";
        try {
          origin = JSON.parse((e as MessageEvent<string>).data).origin ?? "";
        } catch {
          // 형식이 달라도 다시 불러오면 된다
        }
        if (origin !== CLIENT_ID) refresh();
      });
      es.onerror = () => {
        missed = true;
        // 네트워크가 끊긴 경우는 브라우저가 알아서 다시 연결한다(CONNECTING). 응답 오류면 CLOSED
        if (es.readyState === EventSource.CLOSED && source === es) {
          source = null;
          retryLater();
        }
      };
    };

    const onVisibility = () => {
      if (document.visibilityState === "visible") {
        clearInterval(pingTimer);
        open();
      } else {
        close();
        missed = true;
        clearInterval(pingTimer);
        pingTimer = setInterval(() => void authApi.me().catch(() => undefined), HIDDEN_PING_MS);
      }
    };

    onVisibility();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      stopped = true;
      document.removeEventListener("visibilitychange", onVisibility);
      close();
      clearTimeout(refreshTimer);
      clearTimeout(retryTimer);
      clearInterval(pingTimer);
    };
  }, [enabled, qc]);
}
