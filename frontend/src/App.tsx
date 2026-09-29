import { useEffect } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { licenseApi } from "@/api/license";
import LicenseBlocked from "@/features/license/LicenseBlocked";
import { useAuthStore } from "@/store/auth";
import { ProtectedRoute } from "@/routes/ProtectedRoute";
import AppLayout from "@/layouts/AppLayout";
import LoginPage from "@/features/auth/LoginPage";
import ForgotPasswordPage from "@/features/auth/ForgotPasswordPage";
import ResetPasswordPage from "@/features/auth/ResetPasswordPage";
import MyInfoPage from "@/features/me/MyInfoPage";
import DepartmentPage from "@/features/department/DepartmentPage";
import EmployeePage from "@/features/employee/EmployeePage";
import PolicyPage from "@/features/policy/PolicyPage";
import MyLeavesPage from "@/features/leave/MyLeavesPage";
import ApprovalsPage from "@/features/leave/ApprovalsPage";
import CalendarPage from "@/features/calendar/CalendarPage";
import DashboardPage from "@/features/dashboard/DashboardPage";
import ReportsPage from "@/features/report/ReportsPage";
import AuditLogPage from "@/features/audit/AuditLogPage";

export default function App() {
  const loadMe = useAuthStore((s) => s.loadMe);
  const { data: license } = useQuery({ queryKey: ["license"], queryFn: licenseApi.status });

  useEffect(() => {
    void loadMe();
  }, [loadMe]);

  // 라이선스 집행 중이며 무효면 전체 차단 화면
  if (license && license.enforced && !license.valid) {
    return <LicenseBlocked status={license} />;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="my-leaves" element={<MyLeavesPage />} />
          <Route path="me" element={<MyInfoPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={["TEAM_LEAD", "HR_ADMIN", "SUPER_ADMIN"]} />}>
        <Route element={<AppLayout />}>
          <Route path="approvals" element={<ApprovalsPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={["HR_ADMIN", "SUPER_ADMIN"]} />}>
        <Route element={<AppLayout />}>
          <Route path="admin/employees" element={<EmployeePage />} />
          <Route path="admin/departments" element={<DepartmentPage />} />
          <Route path="admin/policy" element={<PolicyPage />} />
          <Route path="admin/reports" element={<ReportsPage />} />
          <Route path="admin/audit" element={<AuditLogPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
