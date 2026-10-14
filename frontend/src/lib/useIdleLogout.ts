import { useEffect, useRef } from "react";
import { create } from "zustand";
import { authApi } from "@/api/auth";
import { useAuthStore } from "@/store/auth";
import { useUnsavedChangesStore } from "@/store/unsavedChanges";

/**
 * 사람이 화면을 쓰고 있다고 보는 입력: 클릭(터치)·키보드·스크롤. 마우스를 움직이기만 한 것은 치지 않는다
 * (책상에서 마우스가 조금만 밀려도 시간이 다시 차지 않게). 스크롤은 요소 안에서 일어나도 잡히게 capture 로 듣는다.
 */
const ACTIVITY_EVENTS = ["pointerdown", "keydown", "wheel", "touchstart", "scroll"] as const;
/** 탭끼리 마지막 입력 시각·자동 로그아웃을 나누는 저장소 키(같은 브라우저의 다른 탭에서 쓰고 있으면 로그아웃하지 않게) */
const ACTIVITY_KEY = "idle.lastActivityAt";
const LOGOUT_KEY = "idle.loggedOutAt";
/** 스크롤처럼 연달아 오는 입력은 매번 기록하지 않고 이 간격으로만 */
const RECORD_EVERY_MS = 1_000;
/** 쓰고 있는 동안 서버 세션도 같은 시간으로 끝나지 않게 알리는 간격(자동 로그아웃 시간보다 충분히 짧게) */
const KEEPALIVE_EVERY_MS = 2 * 60_000;
/** 로그아웃 전에 안내를 띄우는 시간 */
export const IDLE_WARNING_SECONDS = 60;

/** 자동 로그아웃까지 남은 초. 상단 타이머와 안내 창이 함께 본다(꺼져 있으면 null) */
export const useIdleCountdown = create<{ secondsLeft: number | null }>(() => ({ secondsLeft: null }));

export const IDLE_LOGOUT_NOTICE = "오랫동안 사용하지 않아 자동으로 로그아웃되었습니다. 다시 로그인해 주세요.";

function readShared(key: string): number {
  try {
    return Number(localStorage.getItem(key)) || 0;
  } catch {
    return 0;
  }
}

function writeShared(key: string, value: number) {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // 저장소를 못 쓰면 이 탭 기준으로만 센다
  }
}

function setSecondsLeft(secondsLeft: number | null) {
  useIdleCountdown.setState({ secondsLeft });
}

/**
 * 자동 로그아웃: 클릭·키보드·스크롤·터치 입력이 timeoutSeconds 동안 없으면 이 브라우저의 로그인을 끊고 로그인 화면으로 보낸다.
 * <ul>
 *   <li>화면이 알아서 하는 요청(알림 새로 고침·실시간 갱신)은 "사용"으로 치지 않는다. 사람의 입력만 센다.</li>
 *   <li>같은 브라우저의 탭들은 마지막 입력 시각을 나눠 본다: 한 탭에서 쓰고 있으면 다른 탭도 로그아웃하지 않고,
 *       한 탭이 로그아웃하면 나머지 탭도 함께 로그인 화면으로 간다.</li>
 *   <li>절전·잠금으로 타이머가 멈췄다가 깨어나도 시각으로 계산해 바로 로그아웃한다.</li>
 *   <li>쓰고 있는 동안에는 2분마다 서버에 알려 서버 세션(같은 시간)이 먼저 끝나지 않게 한다.</li>
 * </ul>
 * 저장하지 않은 입력은 저장할 수 없으니 묻지 않고 버린다(마지막 1분 동안 안내를 띄운다).
 * 남은 시간은 1초마다 {@link useIdleCountdown} 에 적는다.
 */
export function useIdleLogout(timeoutSeconds: number | undefined) {
  const lastActivity = useRef(Date.now());
  const lastKeepalive = useRef(Date.now());

  useEffect(() => {
    if (!timeoutSeconds || timeoutSeconds <= 0) return;
    const timeoutMs = timeoutSeconds * 1000;
    let done = false;
    lastActivity.current = Date.now();
    lastKeepalive.current = Date.now();
    writeShared(ACTIVITY_KEY, lastActivity.current);
    setSecondsLeft(timeoutSeconds);

    const toLogin = () => {
      if (done) return;
      done = true;
      useUnsavedChangesStore.getState().discardAll();
      useAuthStore.setState({ user: null, signedOutNotice: IDLE_LOGOUT_NOTICE }); // → 로그인 화면(돌아올 주소 기억)
    };

    const logout = () => {
      if (done) return;
      writeShared(LOGOUT_KEY, Date.now());
      void authApi.idleLogout().catch(() => undefined); // 이미 끊겼어도 화면은 로그인으로
      toLogin();
    };

    const onActivity = () => {
      const now = Date.now();
      if (now - lastActivity.current < RECORD_EVERY_MS) return;
      lastActivity.current = now;
      writeShared(ACTIVITY_KEY, now);
      setSecondsLeft(timeoutSeconds); // 다음 1초를 기다리지 않고 타이머를 바로 다시 채운다
      if (now - lastKeepalive.current >= KEEPALIVE_EVERY_MS) {
        lastKeepalive.current = now;
        void authApi.me().catch(() => undefined);
      }
    };

    const tick = () => {
      const last = Math.max(lastActivity.current, readShared(ACTIVITY_KEY));
      const left = timeoutMs - (Date.now() - last);
      if (left <= 0) {
        logout();
      } else {
        setSecondsLeft(Math.ceil(left / 1000));
      }
    };

    const onStorage = (e: StorageEvent) => {
      if (e.key === LOGOUT_KEY && e.newValue) toLogin();
    };

    ACTIVITY_EVENTS.forEach((ev) => window.addEventListener(ev, onActivity, { capture: true, passive: true }));
    window.addEventListener("storage", onStorage);
    const timer = setInterval(tick, 1_000);
    return () => {
      done = true;
      clearInterval(timer);
      ACTIVITY_EVENTS.forEach((ev) => window.removeEventListener(ev, onActivity, { capture: true }));
      window.removeEventListener("storage", onStorage);
      setSecondsLeft(null);
    };
  }, [timeoutSeconds]);
}
