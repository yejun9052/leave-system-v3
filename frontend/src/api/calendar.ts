import { api, unwrap } from "./client";

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

export const calendarApi = {
  events: (start: string, end: string) =>
    unwrap<CalendarEventDto[]>(api.get("/calendar/events", { params: { start, end } })),
  create: (body: CalendarEventInput) =>
    unwrap<CalendarEventDto>(api.post("/calendar/events", body)),
  update: (id: number, body: CalendarEventInput) =>
    unwrap<CalendarEventDto>(api.put(`/calendar/events/${id}`, body)),
  remove: (id: number) => unwrap<void>(api.delete(`/calendar/events/${id}`)),
};
