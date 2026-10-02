import { api, unwrap } from "./client";
import type { LeavePortion, LeaveType } from "@/types";

export type GrantBasis = "HIRE_DATE" | "FISCAL_YEAR";

export interface Policy {
  id: number;
  grantBasis: GrantBasis;
  fiscalStartMonth: number;
  fiscalStartDay: number;
  baseAnnualDays: number;
  seniorityStepYears: number;
  seniorityIncrementDays: number;
  maxAnnualDays: number;
  monthlyAccrualEnabled: boolean;
  monthlyAccrualMax: number;
  allowNegative: boolean;
  halfDayEnabled: boolean;
  hourlyEnabled: boolean;
  maxConcurrentAbsence: number;
  minAdvanceDays: number;
  maxConsecutiveDays: number;
  promotionEnabled: boolean;
  carryOverEnabled: boolean;
  maxCarryOverDays: number;
  /** 다음 연차 기간(다음 기산일 이후) 날짜의 연차 신청 허용 */
  nextPeriodReservationEnabled: boolean;
}

export const policyApi = {
  get: () => unwrap<Policy>(api.get("/policy")),
  update: (body: Omit<Policy, "id">) => unwrap<Policy>(api.put("/policy", body)),
};

// --- 정책 규칙 (포상 / 경조사 / 블랙아웃) ---
export interface AwardRule {
  id: number;
  years: number;
  bonusDays: number;
  name: string | null;
}
export interface SpecialRule {
  id: number;
  name: string;
  days: number;
  leaveTypeCode: string | null;
  sortOrder: number;
}
export interface Blackout {
  id: number;
  startDate: string;
  endDate: string;
  name: string;
}

export const policyRulesApi = {
  awards: () => unwrap<AwardRule[]>(api.get("/policy/award-rules")),
  createAward: (b: Omit<AwardRule, "id">) => unwrap<AwardRule>(api.post("/policy/award-rules", b)),
  updateAward: (id: number, b: Omit<AwardRule, "id">) =>
    unwrap<AwardRule>(api.put(`/policy/award-rules/${id}`, b)),
  removeAward: (id: number) => unwrap<void>(api.delete(`/policy/award-rules/${id}`)),

  specials: () => unwrap<SpecialRule[]>(api.get("/policy/special-rules")),
  createSpecial: (b: Omit<SpecialRule, "id">) =>
    unwrap<SpecialRule>(api.post("/policy/special-rules", b)),
  updateSpecial: (id: number, b: Omit<SpecialRule, "id">) =>
    unwrap<SpecialRule>(api.put(`/policy/special-rules/${id}`, b)),
  removeSpecial: (id: number) => unwrap<void>(api.delete(`/policy/special-rules/${id}`)),

  blackouts: () => unwrap<Blackout[]>(api.get("/policy/blackouts")),
  createBlackout: (b: Omit<Blackout, "id">) => unwrap<Blackout>(api.post("/policy/blackouts", b)),
  updateBlackout: (id: number, b: Omit<Blackout, "id">) =>
    unwrap<Blackout>(api.put(`/policy/blackouts/${id}`, b)),
  removeBlackout: (id: number) => unwrap<void>(api.delete(`/policy/blackouts/${id}`)),
};

/** 촉진 대상: 사용 기한이 N개월 안이고 사용 계획 없는 연차(남은 연차 − 결재 대기)가 있는 재직자 */
export interface PromotionTarget {
  employeeId: number;
  name: string;
  department: string | null;
  hasEmail: boolean;
  /** 연차 기간(그 해에 시작한 기간) */
  year: number;
  periodStart: string;
  /** 사용 기한 */
  periodEnd: string;
  /** 부여(이월 포함) */
  granted: number;
  used: number;
  pending: number;
  remaining: number;
  unplanned: number;
  /** 사용 기한까지 남은 날(기한 당일 0) */
  daysLeft: number;
  /** "2개월 29일" (서버 계산) */
  timeLeft: string;
  /** 이번 연차 기간에 보낸 횟수 */
  noticeCount: number;
  lastNotifiedAt: string | null;
}

export interface PromotionSendResult {
  sent: number;
  mailed: number;
  /** 그 사이 대상이 아니게 돼 건너뛴 인원 */
  skipped: number;
}

export const promotionApi = {
  targets: (months: number) =>
    unwrap<PromotionTarget[]>(api.get("/leave/promotion/targets", { params: { months } })),
  send: (employeeIds: number[]) =>
    unwrap<PromotionSendResult>(api.post("/leave/promotion/send", { employeeIds })),
};

export interface LeaveTypeInput {
  code?: string;
  name: string;
  deductDays: number;
  paid: boolean;
  portion: LeavePortion;
  requiresAnnualExhausted: boolean;
  deductFromAnnual: boolean;
  colorHex: string;
  sortOrder?: number;
  active?: boolean;
}

export const leaveTypeApi = {
  list: (includeInactive = true) =>
    unwrap<LeaveType[]>(api.get("/leave-types", { params: { includeInactive } })),
  create: (body: LeaveTypeInput) => unwrap<LeaveType>(api.post("/leave-types", body)),
  update: (id: number, body: LeaveTypeInput) =>
    unwrap<LeaveType>(api.put(`/leave-types/${id}`, body)),
  remove: (id: number) => unwrap<void>(api.delete(`/leave-types/${id}`)),
};

export const leaveAdminApi = {
  grantAll: (year?: number) =>
    unwrap<{ year: number; granted: number }>(
      api.post("/leave/admin/grant", null, { params: { year } }),
    ),
};
