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
