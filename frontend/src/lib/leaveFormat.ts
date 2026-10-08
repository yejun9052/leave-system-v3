import { HALF_DAY_LABEL, type HalfDayPart, type LeavePortion } from "@/types";

/** 일수를 소수 셋째 자리까지, 불필요한 0 은 제거해 표시한다(0.125 → "0.125"). */
export function formatDays(n: number | null | undefined): string {
  return String(Number((n ?? 0).toFixed(3)));
}

/** 신청 건의 수량 표시: 시간차는 "시간차 2시간", 종일 종류의 반차 신청은 "0.5일 · 오전 반차", 그 외는 "N일". */
export function formatLeaveAmount(r: {
  days: number;
  portion?: LeavePortion;
  hours?: number | null;
  halfDayPart?: HalfDayPart | null;
}): string {
  if (r.portion === "HOURLY" && r.hours != null) {
    return `시간차 ${r.hours}시간`;
  }
  if (r.halfDayPart) {
    return `${formatDays(r.days)}일 · ${HALF_DAY_LABEL[r.halfDayPart]} 반차`;
  }
  return `${formatDays(r.days)}일`;
}

/** 기산일을 걸친 휴가의 기간별 차감: "이번 기간 1일 · 다음 기간 2일", 걸치지 않으면 null. */
export function formatPeriodSplit(r: { currentPeriodDays?: number; nextPeriodDays?: number }): string | null {
  if (!r.nextPeriodDays || r.nextPeriodDays <= 0) return null;
  return `이번 기간 ${formatDays(r.currentPeriodDays ?? 0)}일 · 다음 기간 ${formatDays(r.nextPeriodDays)}일`;
}

/** 경조사 규정으로 신청한 건의 표시: "· 본인 결혼(규정 5일)", 규정이 없으면 빈 문자열. */
export function formatSpecialRule(r: { specialRuleName?: string | null; specialRuleDays?: number | null }): string {
  if (!r.specialRuleName) return "";
  return r.specialRuleDays != null
    ? `· ${r.specialRuleName}(규정 ${formatDays(r.specialRuleDays)}일)`
    : `· ${r.specialRuleName}`;
}
