import { api, unwrap } from "./client";
import type { LeavePortion, LeaveRequestStatus } from "@/types";

export interface CalendarEventDto {
  id: string;
  title: string;
  start: string;
  end: string;
  allDay: boolean;
  scope: "COMPANY" | "DEPARTMENT" | "PERSONAL";
  source: "LEAVE_REQUEST" | "ADMIN_EVENT" | "HOLIDAY";
  colorHex: string;
  departmentId: number | null;
  editable: boolean;
}

export interface CalendarEventInput {
  title: string;
  startDate: string;
  endDate: string;
  allDay: boolean;
  scope: "COMPANY" | "DEPARTMENT";
  departmentId?: number | null;
  colorHex?: string;
}

/** 날짜 상세의 휴가 한 건(사유 없음). 결재 대기 건은 본인·결재자에게만 온다. */
export interface DayLeaveDto {
  id: number;
  employeeId: number;
  employeeName: string;
  departmentId: number | null;
  departmentName: string | null;
  leaveTypeName: string;
  leaveTypeColor: string;
  portion: LeavePortion;
  hours: number | null;
  startDate: string;
  endDate: string;
  status: LeaveRequestStatus;
  mine: boolean;
}

export interface DayEventDto {
  id: string;
  title: string;
  /** 포함 범위(마지막 날) */
  start: string;
  end: string;
  scope: "COMPANY" | "DEPARTMENT" | "PERSONAL";
  colorHex: string;
}

export interface DayDetailDto {
  date: string;
  holidayName: string | null;
  leaves: DayLeaveDto[];
  events: DayEventDto[];
}

export const calendarApi = {
  events: (start: string, end: string) =>
    unwrap<CalendarEventDto[]>(api.get("/calendar/events", { params: { start, end } })),
  day: (date: string) => unwrap<DayDetailDto>(api.get("/calendar/day", { params: { date } })),
  create: (body: CalendarEventInput) =>
    unwrap<CalendarEventDto>(api.post("/calendar/events", body)),
  update: (id: number, body: CalendarEventInput) =>
    unwrap<CalendarEventDto>(api.put(`/calendar/events/${id}`, body)),
  remove: (id: number) => unwrap<void>(api.delete(`/calendar/events/${id}`)),
};
