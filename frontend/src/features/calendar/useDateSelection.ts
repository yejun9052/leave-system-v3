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

// 오늘·로컬 날짜는 다른 화면도 쓰므로 lib/dateFormat 에 둔다
export { localDateString, todayString } from "@/lib/dateFormat";

/** "YYYY-MM" 달을 delta 만큼 옮긴다("2026-12", 1 → "2027-01"). */
export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number);
  const d = new Date(Date.UTC(y, m - 1 + delta, 1));
  return d.toISOString().slice(0, 7);
}

/**
 * 월 격자에 그릴 날짜들: 그 달 1일이 있는 주의 일요일부터 말일이 있는 주의 토요일까지(필요한 주 수만큼).
 * 앞뒤 달 날짜도 칸을 채우려고 포함한다.
 */
export function monthGridDates(month: string): string[] {
  const first = `${month}-01`;
  const start = addDays(first, -toUtc(first).getUTCDay());
  const last = addDays(`${shiftMonth(month, 1)}-01`, -1);
  const end = addDays(last, 6 - toUtc(last).getUTCDay());
  const dates: string[] = [];
  for (let d = start; d <= end; d = addDays(d, 1)) dates.push(d);
  return dates;
}

/** 달력에서 고른 날짜들(정렬, 중복 없음). 이어진 묶음마다 신청 한 건이 된다(lib/dateSpans) */
export type DateSelection = string[];

export type ToggleResult = { ok: true; dates: DateSelection } | { ok: false; message: string };

export const NOT_WORKDAY_MESSAGE = "휴가는 근무일만 고를 수 있습니다(주말·공휴일 제외).";
export const PARTIAL_ONE_DAY_MESSAGE = "반차·시간차는 하루만 신청할 수 있습니다.";

/**
 * 날짜 클릭 규칙(윈도우의 Ctrl+클릭과 같은 개념, 2026-10-14):
 * <ul>
 *   <li>누를 때마다 그 날짜를 넣고 뺀다. 드래그·범위 선택은 없다</li>
 *   <li>하루만(반차·시간차): 누른 날짜 하나로 바꾼다. 이미 고른 그 날을 다시 누르면 뺀다</li>
 *   <li>고를 수 없는 날(휴가는 주말·공휴일)은 넣지 않고 안내한다. 빼기는 언제나 된다</li>
 * </ul>
 */
export function toggleDate(
  prev: DateSelection,
  date: string,
  single: boolean,
  canPick: (date: string) => boolean,
): ToggleResult {
  if (prev.includes(date)) return { ok: true, dates: prev.filter((d) => d !== date) };
  if (!canPick(date)) return { ok: false, message: NOT_WORKDAY_MESSAGE };
  return { ok: true, dates: single ? [date] : [...prev, date].sort() };
}

/** 선택 상태와 규칙을 묶은 훅. 규칙 위반이면 onReject 로 안내 문구를 넘긴다. */
export function useDateSelection(canPick: (date: string) => boolean, onReject: (message: string) => void) {
  const [dates, setDates] = useState<DateSelection>([]);

  /** 누른 날짜 넣기/빼기. 바뀐 뒤 고른 날짜를 돌려준다(안 바뀌면 null) */
  const toggle = useCallback(
    (date: string, single: boolean): DateSelection | null => {
      const result = toggleDate(dates, date, single, canPick);
      if (!result.ok) {
        onReject(result.message);
        return null;
      }
      setDates(result.dates);
      return result.dates;
    },
    [dates, canPick, onReject],
  );

  /** 그 날짜 하나로 새로 고른다(우클릭 "이 날짜로 신청하기", 모바일 "+ 신청"). */
  const startAt = useCallback(
    (date: string): boolean => {
      if (!canPick(date)) {
        onReject(NOT_WORKDAY_MESSAGE);
        return false;
      }
      setDates([date]);
      return true;
    },
    [canPick, onReject],
  );

  /** 하루만 신청하는 종류로 바꾸면 첫 날짜 하나만 남긴다. 줄였으면 true. */
  const keepFirst = useCallback((): boolean => {
    if (dates.length <= 1) return false;
    setDates([dates[0]]);
    return true;
  }, [dates]);

  return { dates, setDates, toggle, startAt, keepFirst, clear: () => setDates([]) };
}
