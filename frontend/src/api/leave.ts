import { api, unwrap } from "./client";
import type { LeaveBalance, LeaveRequest, LeaveType, Page } from "@/types";

export interface LeaveRequestCreate {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  reason?: string;
}

export const leaveApi = {
  activeTypes: () =>
    unwrap<LeaveType[]>(api.get("/leave-types", { params: { includeInactive: false } })),

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
