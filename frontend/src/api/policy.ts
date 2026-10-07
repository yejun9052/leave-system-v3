import { api, unwrap } from "./client";
import type { AnnualDeductionMode, LeavePortion, LeaveRequestStatus, LeaveType } from "@/types";

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
  /** 연차 촉진 자동 발송 ON/OFF (자동화 탭에서 따로 저장, 연차 정책 저장에는 반영되지 않음) */
  promotionEnabled: boolean;
  /** 자동 발송 시기: 사용 기한 몇 개월 전(큰 값부터) */
  promotionMonths: number[];
  carryOverEnabled: boolean;
  maxCarryOverDays: number;
  /** 다음 연차 기간(다음 기산일 이후) 날짜의 연차 신청 허용 */
  nextPeriodReservationEnabled: boolean;
  /** 금지 기간을 등록·늘려 수정할 때 겹치는 기존 휴가 처리 방식 */
  blackoutConflictMode: BlackoutConflictMode;
}

/** 금지 기간 등록 시 기존 휴가 처리: 승인된 휴가만 유지(기본) / 모두 취소. 결재 대기는 둘 다 자동 반려 */
export type BlackoutConflictMode = "KEEP_APPROVED" | "CANCEL_ALL";

export const BLACKOUT_CONFLICT_LABEL: Record<BlackoutConflictMode, string> = {
  KEEP_APPROVED: "승인된 휴가만 유지",
  CANCEL_ALL: "모두 취소",
};

/** 금지 기간 등록·수정 전 미리보기의 휴가 한 건. action: 자동 반려 / 자동 취소 / 유지 */
export interface BlackoutImpactItem {
  requestId: number;
  employeeName: string;
  departmentName: string | null;
  leaveTypeName: string;
  startDate: string;
  endDate: string;
  days: number;
  status: LeaveRequestStatus;
  action: "REJECT" | "CANCEL" | "KEEP";
}

/** 저장하면 affected 를 처리하고 kept 는 그대로 둔다. extended: 기간을 늘려 수정하는 경우 */
export interface BlackoutImpact {
  mode: BlackoutConflictMode;
  extended: boolean;
  affected: BlackoutImpactItem[];
  kept: BlackoutImpactItem[];
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
  /** 연간 사용 횟수(1~12월, 휴가 시작일 기준). null 이면 제한 없음. 예: 생일 반차 1회 */
  annualLimit: number | null;
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
  /** 등록·수정 전 미리보기(저장하지 않음). id: 수정하는 금지 기간 */
  blackoutImpact: (startDate: string, endDate: string, id?: number) =>
    unwrap<BlackoutImpact>(api.get("/policy/blackouts/impact", { params: { startDate, endDate, id } })),
};

/** 촉진 대상: 사용 기한이 N개월 안이고 남은 연차가 있는 재직자 */
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
  /** 결재 대기 중인 차감 예정(남은 연차에서 아직 빼지 않음) */
  pending: number;
  remaining: number;
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
  /** keyword: 이름·부서 검색(공백으로 나눈 단어 모두, 상위 부서로 찾으면 하위 부서 포함) */
  /** departmentIds: 부서 트리에서 체크한 부서(그 부서 소속만), 비면 조건 없음 */
  targets: (months: number, keyword?: string, departmentIds: number[] = []) =>
    unwrap<PromotionTarget[]>(
      api.get("/leave/promotion/targets", {
        params: { months, keyword: keyword || undefined, departmentIds: departmentIds.join(",") || undefined },
      }),
    ),
  send: (employeeIds: number[]) =>
    unwrap<PromotionSendResult>(api.post("/leave/promotion/send", { employeeIds })),
};

/** 자동 작업 상태: 항상 실행 / 켜짐 / 꺼짐 / 설정 없음(실행해도 건너뜀) */
export type AutomationJobState = "ALWAYS" | "ON" | "OFF" | "NOT_CONFIGURED";

export interface AutomationJob {
  key: string;
  name: string;
  description: string;
  /** "매일 01:00" */
  schedule: string;
  state: AutomationJobState;
  lastStartedAt: string | null;
  lastFinishedAt: string | null;
  /** 성공 true, 실패 false, 건너뜀 null */
  lastSuccess: boolean | null;
  lastMessage: string | null;
}

/** 지금 자동 발송을 돌리면 보낼 직원 */
export interface AutoPromotionTarget {
  employeeId: number;
  name: string;
  department: string | null;
  periodEnd: string;
  timeLeft: string;
  /** 이번에 보낼 발송 시기(사용 기한 N개월 전) */
  stageMonths: number;
}

export interface AutomationOverview {
  promotionEnabled: boolean;
  promotionMonths: number[];
  promotionPreview: AutoPromotionTarget[];
  jobs: AutomationJob[];
}

export const automationApi = {
  get: () => unwrap<AutomationOverview>(api.get("/policy/automation")),
  updatePromotion: (promotionEnabled: boolean, promotionMonths: number[]) =>
    unwrap<AutomationOverview>(api.put("/policy/automation/promotion", { promotionEnabled, promotionMonths })),
};

export interface LeaveTypeInput {
  code?: string;
  name: string;
  deductDays: number;
  paid: boolean;
  portion: LeavePortion;
  annualDeductionMode: AnnualDeductionMode;
  allowedDuringBlackout: boolean;
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
