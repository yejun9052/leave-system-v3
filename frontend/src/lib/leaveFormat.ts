import type { LeavePortion } from "@/types";

/** 일수를 소수 셋째 자리까지, 불필요한 0 은 제거해 표시한다(0.125 → "0.125"). */
export function formatDays(n: number | null | undefined): string {
  return String(Number((n ?? 0).toFixed(3)));
}

/** 신청 건의 수량 표시: 시간차는 "시간차 2시간", 그 외는 "N일". */
export function formatLeaveAmount(r: { days: number; portion?: LeavePortion; hours?: number | null }): string {
  if (r.portion === "HOURLY" && r.hours != null) {
    return `시간차 ${r.hours}시간`;
  }
  return `${formatDays(r.days)}일`;
}

/** 경조사 규정으로 신청한 건의 표시: "· 본인 결혼(규정 5일)", 규정이 없으면 빈 문자열. */
export function formatSpecialRule(r: { specialRuleName?: string | null; specialRuleDays?: number | null }): string {
  if (!r.specialRuleName) return "";
  return r.specialRuleDays != null
    ? `· ${r.specialRuleName}(규정 ${formatDays(r.specialRuleDays)}일)`
    : `· ${r.specialRuleName}`;
}
