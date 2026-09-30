import { useCallback, useState } from "react";

/** 날짜는 모두 "YYYY-MM-DD" 문자열로 다룬다(시간대에 따라 하루씩 밀리지 않도록 UTC 기준 계산). */
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function toUtc(date: string): Date {
  return new Date(`${date}T00:00:00Z`);
}

export function addDays(date: string, days: number): string {
  const d = toUtc(date);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function isWeekend(date: string): boolean {
  const dow = toUtc(date).getUTCDay();
  return dow === 0 || dow === 6;
}

export function weekdayLabel(date: string): string {
  return WEEKDAYS[toUtc(date).getUTCDay()];
}

/** "2026-10-05 (월)" */
export function formatDateWithWeekday(date: string): string {
  return `${date} (${weekdayLabel(date)})`;
}

/** "2026년 10월 5일 (월)" */
export function formatKoreanDate(date: string): string {
  const [y, m, d] = date.split("-").map(Number);
  return `${y}년 ${m}월 ${d}일 (${weekdayLabel(date)})`;
}

/** 로컬 시간 기준 오늘 "YYYY-MM-DD" */
export function todayString(): string {
  const now = new Date();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${m}-${d}`;
}

/** 캘린더 신청 선택. end 가 null 이면 시작일만 고른 상태(그대로 신청하면 그 하루). */
export interface DateSelection {
  start: string;
  end: string | null;
}

export type SelectResult =
  | { ok: true; selection: DateSelection }
  | { ok: false; message: string };

export const NOT_WORKDAY_MESSAGE = "시작일은 근무일이어야 합니다.";
export const PARTIAL_ONE_DAY_MESSAGE = "반차·반반차·시간차는 하루만 신청할 수 있습니다.";

/**
 * 날짜 클릭 규칙.
 * <ul>
 *   <li>첫 클릭: 시작일(그대로 신청하면 그 하루)</li>
 *   <li>두 번째 클릭: 종료일. 시작일보다 이르면 두 날짜를 바꿔 이른 쪽이 시작일</li>
 *   <li>세 번째 클릭: 그 날짜로 새로 시작</li>
 *   <li>부분 휴가(반차·반반차·시간차): 누를 때마다 그 하루만</li>
 *   <li>시작일이 될 날짜가 주말·공휴일이면 선택하지 않는다(서버 규칙과 같음)</li>
 * </ul>
 */
export function nextSelection(
  prev: DateSelection | null,
  date: string,
  partial: boolean,
  isWorkday: (date: string) => boolean,
): SelectResult {
  const startFresh = (): SelectResult =>
    isWorkday(date) ? { ok: true, selection: { start: date, end: null } } : { ok: false, message: NOT_WORKDAY_MESSAGE };

  if (partial || !prev || prev.end !== null) {
    return startFresh();
  }
  if (date < prev.start) {
    // 거꾸로 누른 경우: 누른 날짜가 새 시작일이 된다
    return isWorkday(date)
      ? { ok: true, selection: { start: date, end: prev.start } }
      : { ok: false, message: NOT_WORKDAY_MESSAGE };
  }
  return { ok: true, selection: { start: prev.start, end: date } };
}

/** 선택 상태와 규칙을 묶은 훅. 규칙 위반이면 onReject 로 안내 문구를 넘긴다. */
export function useDateSelection(isWorkday: (date: string) => boolean, onReject: (message: string) => void) {
  const [selection, setSelection] = useState<DateSelection | null>(null);

  const pick = useCallback(
    (date: string, partial: boolean): boolean => {
      const result = nextSelection(selection, date, partial, isWorkday);
      if (!result.ok) {
        onReject(result.message);
        return false;
      }
      setSelection(result.selection);
      return true;
    },
    [selection, isWorkday, onReject],
  );

  /** 그 날짜로 선택을 새로 시작(우클릭 "이 날짜로 신청하기", 모바일 "+ 신청"). */
  const startAt = useCallback(
    (date: string): boolean => {
      if (!isWorkday(date)) {
        onReject(NOT_WORKDAY_MESSAGE);
        return false;
      }
      setSelection({ start: date, end: null });
      return true;
    },
    [isWorkday, onReject],
  );

  /** 부분 휴가로 바꾸면 시작일 하루로 줄인다. 줄였으면 true. */
  const collapseToStart = useCallback((): boolean => {
    if (!selection || selection.end === null || selection.end === selection.start) return false;
    setSelection({ start: selection.start, end: null });
    return true;
  }, [selection]);

  return { selection, setSelection, pick, startAt, collapseToStart, clear: () => setSelection(null) };
}
