export type Role = "SUPER_ADMIN" | "HR_ADMIN" | "TEAM_LEAD" | "EMPLOYEE";

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: "시스템관리자",
  HR_ADMIN: "인사관리자",
  TEAM_LEAD: "팀장",
  EMPLOYEE: "사원",
};

export interface Me {
  id: number;
  email: string;
  name: string;
  position: string | null;
  phone: string | null;
  departmentId: number | null;
  departmentName: string | null;
  roles: Role[];
  /** true 면 비밀번호를 바꾸기 전까지 다른 기능 사용 불가(서버가 403 으로 차단) */
  passwordChangeRequired: boolean;
}

export type EmployeeStatus = "ACTIVE" | "ON_LEAVE" | "RESIGNED";

export interface Department {
  id: number;
  name: string;
  parentId: number | null;
  leadId: number | null;
  leadName: string | null;
  sortOrder: number;
  memberCount: number;
  children: Department[];
}

export interface Employee {
  id: number;
  email: string;
  name: string;
  employeeNo: string | null;
  departmentId: number | null;
  departmentName: string | null;
  position: string | null;
  phone: string | null;
  hireDate: string;
  status: EmployeeStatus;
  roles: Role[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ---- Leave domain ----
export type LeaveRequestStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "CANCEL_REQUESTED"
  | "CANCELLED";

export const LEAVE_STATUS_LABEL: Record<LeaveRequestStatus, string> = {
  PENDING: "대기",
  APPROVED: "승인",
  REJECTED: "반려",
  CANCEL_REQUESTED: "취소대기",
  CANCELLED: "취소",
};

export interface LeaveType {
  id: number;
  code: string;
  name: string;
  deductDays: number;
  paid: boolean;
  halfDay: boolean;
  deductFromAnnual: boolean;
  colorHex: string;
  sortOrder: number;
  active: boolean;
}

export interface LeaveBalance {
  year: number;
  granted: number;
  used: number;
  pending: number;
  carriedOver: number;
  expired: number;
  remaining: number;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  departmentName: string | null;
  leaveTypeId: number;
  leaveTypeName: string;
  leaveTypeColor: string;
  startDate: string;
  endDate: string;
  days: number;
  status: LeaveRequestStatus;
  reason: string | null;
  approverName: string | null;
  approvedAt: string | null;
  rejectReason: string | null;
  cancelReason: string | null;
  createdAt: string;
}

export interface CalendarEvent {
  id: number;
  title: string;
  start: string;
  end: string;
  allDay: boolean;
  scope: "COMPANY" | "DEPARTMENT" | "PERSONAL";
  source: "LEAVE_REQUEST" | "ADMIN_EVENT" | "HOLIDAY";
  colorHex: string;
  employeeName: string | null;
  departmentId: number | null;
  editable: boolean;
}
