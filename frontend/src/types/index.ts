export type Role = "SYSTEM_ADMIN" | "HR_ADMIN" | "TEAM_LEAD" | "EMPLOYEE";

export const ROLE_LABEL: Record<Role, string> = {
  SYSTEM_ADMIN: "시스템관리자",
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
  /** true 면 직원이 아닌 관리 전용 계정(연차·휴가 신청 대상 아님) */
  systemAccount: boolean;
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

export interface LeaveTypeSpecialRule {
  id: number;
  name: string;
  days: number;
}

export type LeavePortion = "FULL" | "HALF" | "QUARTER" | "HOURLY";

export interface LeaveType {
  id: number;
  code: string;
  name: string;
  deductDays: number;
  paid: boolean;
  halfDay: boolean;
  portion: LeavePortion;
  requiresAnnualExhausted: boolean;
  /** 현재 정책에서 사용 가능한 종류인지 */
  policyEnabled: boolean;
  /** 연결된 경조사 규정(없으면 빈 배열). 있으면 신청 때 하나를 반드시 선택 */
  specialRules: LeaveTypeSpecialRule[];
  deductFromAnnual: boolean;
  colorHex: string;
  sortOrder: number;
  active: boolean;
}

export interface LeaveBalance {
  /** 그 해에 시작한 연차 기간(입사일 기준이면 입사 기념일부터 1년) */
  year: number;
  periodStart: string;
  /** 사용 기한(기간 마지막 날) */
  periodEnd: string;
  granted: number;
  used: number;
  pending: number;
  carriedOver: number;
  expired: number;
  remaining: number;
  /** 다음 기간에서 뺄 예약분(승인 + 결재 대기). 지금 기간에만 채워짐 */
  nextPeriodReserved: number;
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
  portion: LeavePortion;
  /** 시간차만 숫자, 그 외 null */
  hours: number | null;
  /** 승인으로 소멸되는 연차(병가·공가) */
  forfeitedDays: number;
  /** 경조사 규정으로 신청한 경우의 규정 이름·일수 */
  specialRuleName: string | null;
  specialRuleDays: number | null;
  /** 결재함에서만 채워지는 결재자용 경고(예: 경조사가 팀 동시 부재 한도 초과) */
  approvalWarning?: string | null;
  /** 신청 응답: 결재할 인사관리자가 없는 경우의 안내 */
  requestWarning?: string | null;
  /** 결재함 응답에서만 채워짐: 결재자 본인의 신청인지(자가 승인 건) */
  ownRequest?: boolean | null;
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
