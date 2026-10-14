import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { holidayApi } from "@/api/holidays";
import { todayString } from "@/lib/dateFormat";

/**
 * 달력에서 여러 날짜를 골라 신청할 때의 묶음 규칙(2026-10-14 결정, docs/decisions/2026-10-14-multi-date-selection.md).
 * 고른 날짜는 이어진 묶음마다 신청(일정·금지 기간) 한 건이 된다. 날짜는 모두 "YYYY-MM-DD".
 */

/** 이어진 묶음 하나(양 끝 포함). 서버 DateSpan 과 같은 모양 */
export interface DateSpan {
  startDate: string;
  endDate: string;
}

function toUtc(date: string): Date {
  return new Date(`${date}T00:00:00Z`);
}

function nextDate(date: string): string {
  const d = toUtc(date);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

export function isWeekendDate(date: string): boolean {
  const dow = toUtc(date).getUTCDay();
  return dow === 0 || dow === 6;
}

/**
 * 고른 날짜를 이어진 묶음으로 나눈다.
 *
 * @param bridge 두 날짜 사이에 이 날만 있으면 이어진 것으로 본다(휴가·금지 기간: 주말·공휴일). 일정은 비워 두어
 *               달력 날짜가 바로 이어져야 한 묶음이다
 */
export function groupDates(dates: Iterable<string>, bridge: (date: string) => boolean = () => false): DateSpan[] {
  const sorted = [...new Set(dates)].sort();
  const spans: DateSpan[] = [];
  for (const date of sorted) {
    const last = spans[spans.length - 1];
    if (last) {
      let d = nextDate(last.endDate);
      while (d < date && bridge(d)) d = nextDate(d);
      if (d === date) {
        last.endDate = date;
        continue;
      }
    }
    spans.push({ startDate: date, endDate: date });
  }
  return spans;
}

/** "10/11" (해가 바뀌는 선택이면 "2027/1/2") */
function short(date: string, withYear: boolean): string {
  const [y, m, d] = date.split("-").map(Number);
  return withYear ? `${y}/${m}/${d}` : `${m}/${d}`;
}

/**
 * 묶음 표시: "10/11 ~ 10/13, 10/15". 올해가 아닌 날짜가 섞이면 연도를 붙인다.
 */
export function formatSpans(spans: DateSpan[], today: string = todayString()): string {
  const thisYear = today.slice(0, 4);
  const withYear = spans.some((s) => s.startDate.slice(0, 4) !== thisYear || s.endDate.slice(0, 4) !== thisYear);
  return spans
    .map((s) =>
      s.startDate === s.endDate
        ? short(s.startDate, withYear)
        : `${short(s.startDate, withYear)} ~ ${short(s.endDate, withYear)}`,
    )
    .join(", ");
}

/** 미리보기 쿼리 문자열: "2026-10-11~2026-10-13" */
export function spanParam(s: DateSpan): string {
  return s.startDate === s.endDate ? s.startDate : `${s.startDate}~${s.endDate}`;
}

/**
 * 고른 날짜들의 연도(앞뒤 하루 포함, 묶음 사이 공휴일을 보려고)에 해당하는 공휴일. 보이는 달 밖의 날짜를 묶을 때도 쓴다.
 */
export function useHolidaySet(dates: string[]): Set<string> {
  const years = useMemo(() => {
    const set = new Set<number>();
    for (const d of dates) set.add(Number(d.slice(0, 4)));
    return [...set].sort();
  }, [dates]);
  const results = useQueries({
    queries: years.map((year) => ({
      queryKey: ["holidays", year],
      queryFn: () => holidayApi.list(year),
      staleTime: 10 * 60_000,
    })),
  });
  const key = results.map((r) => r.dataUpdatedAt).join(",");
  return useMemo(
    () => new Set(results.flatMap((r) => r.data ?? []).map((h) => h.date)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [key],
  );
}
