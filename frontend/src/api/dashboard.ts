import { api, unwrap } from "./client";
import type { LeaveBalance, LeaveRequest } from "@/types";

export interface PersonalDashboard {
  balance: LeaveBalance;
  pendingCount: number;
  upcoming: LeaveRequest[];
  teamOnLeaveToday: number;
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
  usageExportUrl: (year: number) => `/api/reports/leave-usage/export?year=${year}`,
};
