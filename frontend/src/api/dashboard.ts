import { api, unwrap } from "./client";
import type { LeaveBalance, LeavePortion, LeaveRequest } from "@/types";

export interface PersonalDashboard {
  balance: LeaveBalance;
  pendingCount: number;
  upcoming: LeaveRequest[];
  teamOnLeaveToday: number;
  /** 이번 주(월~일) 같은 부서 동료(본인 제외)의 승인된 휴가, 시작일 순 */
  teamLeaves: TeamLeave[];
}

export interface TeamLeave {
  employeeId: number;
  employeeName: string;
  leaveTypeName: string;
  portion: LeavePortion;
  startDate: string;
  endDate: string;
}

export interface AdminDashboard {
  totalEmployees: number;
  onLeaveToday: number;
  pendingApprovals: number;
  totalGranted: number;
  totalUsed: number;
  usageRate: number;
  monthlyUsage: { month: number; days: number }[];
  departmentUsage: { departmentName: string; days: number }[];
}

export const dashboardApi = {
  personal: () => unwrap<PersonalDashboard>(api.get("/dashboard/me")),
  admin: () => unwrap<AdminDashboard>(api.get("/dashboard/admin")),
};

export const reportApi = {
  /** 부서·사용자를 고르면 그 대상만(둘을 합침), 아무것도 고르지 않으면 전체 */
  usageExportUrl: (year: number, departmentIds: number[] = [], employeeIds: number[] = []) => {
    const params = new URLSearchParams({ year: String(year) });
    departmentIds.forEach((id) => params.append("departmentIds", String(id)));
    employeeIds.forEach((id) => params.append("employeeIds", String(id)));
    return `/api/reports/leave-usage/export?${params}`;
  },
};
