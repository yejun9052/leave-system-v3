import { api, unwrap } from "./client";
import type { HalfDayPart, LeaveBalance, LeaveRequest, LeaveRequestStatus, LeaveType, Page } from "@/types";

export interface LeaveRequestCreate {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  reason?: string;
  /** 시간차(1~3)일 때만 전송 */
  hours?: number;
  /** 소멸분이 있는 병가·공가면 true */
  forfeitAcknowledged?: boolean;
  /** 종류에 경조사 규정이 있으면 필수 */
  specialRuleId?: number;
  /** 0.5일 경조사 규정(예: 생일)이면 필수인 오전·오후 */
  halfDayPart?: HalfDayPart;
}

/** 인사관리자·시스템 관리자의 강제 등록: 바로 승인 상태로 등록(지난 날짜 가능, 시작일은 근무일만) */
export interface LeaveRegister {
  employeeId: number;
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  reason?: string;
  /** 시간차(1~3)일 때만 전송 */
  hours?: number;
  /** 종류에 경조사 규정이 있으면 필수 */
  specialRuleId?: number;
  /** 0.5일 경조사 규정(예: 생일)이면 필수인 오전·오후 */
  halfDayPart?: HalfDayPart;
}

export interface LeaveEligibility {
  allowed: boolean;
  reason: string | null;
  remainingDays: number | null;
  forfeitDays: number;
  /** 미리보기(종료일까지 준 경우)에서만 채워짐: 기간 근무일 수 */
  workdays: number | null;
  /** 미리보기: 이번 신청의 연차 차감 예정 */
  deduction: number | null;
  /** 미리보기: 결재 대기 중인 다른 신청의 차감 예정 합계 */
  pendingDays: number | null;
  /** 미리보기: 신청 후 잔여(잔여 − 대기 − 이번 차감(시작일 기간 몫) − 소멸 예정) */
  remainingAfter: number | null;
  /** 미리보기: 시작일이 속한 연차 기간 */
  periodStart: string | null;
  periodEnd: string | null;
  /** 미리보기: 차감 중 다음 연차 기간(periodEnd 다음 날부터)에서 뺄 몫 */
  nextPeriodDeduction: number | null;
}

export interface LeavePreviewParams {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  hours?: number;
  specialRuleId?: number;
  halfDayPart?: HalfDayPart;
}

/**
 * 신청자의 결재 경로. 한 명이 승인하면 확정되고, 어느 경우든 인사관리자도 결재할 수 있다.
 * LEAD = 결재 팀장(leadName), HR = 결재 팀장이 없어 인사관리자, SELF = 본인 자가 승인 가능
 */
export interface ApprovalRoute {
  approverKind: "LEAD" | "HR" | "SELF";
  leadName: string | null;
}

export interface LeaveSearchParams {
  keyword?: string;
  /** 비면 전체 상태 */
  statuses?: LeaveRequestStatus[];
  /** 휴가 기간이 from~to 와 겹치는 것 */
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const leaveApi = {
  activeTypes: () =>
    unwrap<LeaveType[]>(api.get("/leave-types", { params: { includeInactive: false } })),

  approvalRoute: () => unwrap<ApprovalRoute>(api.get("/leave-requests/approval-route")),

  eligibility: (leaveTypeId: number, startDate?: string) =>
    unwrap<LeaveEligibility>(
      api.get("/leave-requests/eligibility", { params: { leaveTypeId, startDate } }),
    ),

  /** 신청 미리보기: 신청과 같은 계산으로 근무일·차감·신청 후 잔여와 불가 사유(저장 안 함) */
  preview: (params: LeavePreviewParams) =>
    unwrap<LeaveEligibility>(api.get("/leave-requests/eligibility", { params })),

  create: (body: LeaveRequestCreate) =>
    unwrap<LeaveRequest>(api.post("/leave-requests", body)),

  register: (body: LeaveRegister) =>
    unwrap<LeaveRequest>(api.post("/leave-requests/register", body)),

  myRequests: (page = 0, size = 20) =>
    unwrap<Page<LeaveRequest>>(api.get("/leave-requests/me", { params: { page, size } })),

  pending: () => unwrap<LeaveRequest[]>(api.get("/leave-requests/pending")),

  /** 결재함 휴가 목록: 팀장은 맡은 부서(하위 포함)만, 인사관리자·시스템 관리자는 전체. 휴가 시작일 최신순 */
  search: (params: LeaveSearchParams) =>
    unwrap<Page<LeaveRequest>>(
      api.get("/leave-requests", {
        params: { ...params, statuses: params.statuses?.join(",") || undefined },
      }),
    ),

  approve: (id: number) => unwrap<LeaveRequest>(api.post(`/leave-requests/${id}/approve`)),
  reject: (id: number, reason: string) =>
    unwrap<LeaveRequest>(api.post(`/leave-requests/${id}/reject`, { reason })),
  cancel: (id: number, reason?: string) =>
    unwrap<LeaveRequest>(api.post(`/leave-requests/${id}/cancel`, { reason })),
  approveCancel: (id: number) =>
    unwrap<LeaveRequest>(api.post(`/leave-requests/${id}/cancel/approve`)),
  rejectCancel: (id: number, reason: string) =>
    unwrap<LeaveRequest>(api.post(`/leave-requests/${id}/cancel/reject`, { reason })),

  myBalance: (year?: number) =>
    unwrap<LeaveBalance>(api.get("/leave-requests/balances/me", { params: { year } })),
  myBalanceHistory: () =>
    unwrap<LeaveBalance[]>(api.get("/leave-requests/balances/me/history")),
};

export const notificationApi = {
  list: (limit = 20) =>
    unwrap<
      {
        id: number;
        type: string;
        title: string;
        message: string | null;
        link: string | null;
        read: boolean;
        createdAt: string;
      }[]
    >(api.get("/notifications", { params: { limit } })),
  unreadCount: () => unwrap<{ count: number }>(api.get("/notifications/unread-count")),
  readAll: () => unwrap<void>(api.post("/notifications/read-all")),
};
