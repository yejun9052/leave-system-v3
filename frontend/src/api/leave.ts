import { api, unwrap } from "./client";
import type { LeaveBalance, LeaveRequest, LeaveType, Page } from "@/types";

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
  /** 팀장 부재로 인사관리자에게 바로 신청할 때만(최대 500자) */
  hrDirectReason?: string;
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
  /** 미리보기: 신청 후 잔여(잔여 − 대기 − 이번 차감 − 소멸 예정) */
  remainingAfter: number | null;
}

export interface LeavePreviewParams {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  hours?: number;
  specialRuleId?: number;
}

export interface ApprovalRoute {
  leadApprovalRequired: boolean;
  firstStage: "LEAD" | "HR";
  leadName: string | null;
  leadAbsent: boolean;
  leadAbsenceType: string | null;
  hrDirectAvailable: boolean;
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

  myRequests: (page = 0, size = 20) =>
    unwrap<Page<LeaveRequest>>(api.get("/leave-requests/me", { params: { page, size } })),

  pending: () => unwrap<LeaveRequest[]>(api.get("/leave-requests/pending")),

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
