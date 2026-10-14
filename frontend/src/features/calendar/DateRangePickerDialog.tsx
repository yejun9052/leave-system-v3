import { useCallback, useMemo, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DatesSetArg, EventInput } from "@fullcalendar/core";
import type { DateClickArg } from "@fullcalendar/interaction";
import { useQuery } from "@tanstack/react-query";
import { calendarApi } from "@/api/calendar";
import { leaveApi } from "@/api/leave";
import { policyRulesApi } from "@/api/policy";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { formatSpans, groupDates, useHolidaySet, type DateSpan } from "@/lib/dateSpans";
import { addDays, isWeekend, localDateString, todayString, toggleDate } from "./useDateSelection";

/** 공휴일·연차 제한 막대 색(캘린더와 같음) */
const HOLIDAY_COLOR = "#ef4444";

/**
 * 일정만 보이는 달력에서 날짜를 고르는 창(블랙아웃 등록·휴가 직접 등록·내 휴가 신청).
 * 공휴일·회사 일정·팀 일정·연차 제한 기간만 보여 주고 휴가는 빼서 날짜를 고르기 쉽게 한다.
 * 휴가 직접 등록이면 등록할 직원의 휴가(승인·취소 요청 중·결재 대기)만 함께 보여 겹치지 않게 고르게 한다.
 * 고르는 규칙은 캘린더와 같다: 누를 때마다 그 날짜를 넣고 뺀다. 고른 날짜는 이어진 묶음으로 넘긴다.
 *
 * @param single      하루만 고른다(반차·시간차 등)
 * @param workdayOnly 근무일만 고른다(휴가). 블랙아웃은 아무 날이나
 * @param bridge      묶음 규칙: "workday" 면 사이에 주말·공휴일만 있으면 이어진 것(휴가·금지 기간), "none" 이면 바로 이어진 날만
 * @param oneSpan     이어진 묶음 하나만(금지 기간 수정)
 * @param excludeBlackoutId 수정 중인 금지 기간은 달력에 그리지 않는다
 * @param employee    휴가 직접 등록에서 고른 직원: 그 사람의 휴가를 함께 그린다
 */
export default function DateRangePickerDialog({
  title,
  initialDates = [],
  single = false,
  workdayOnly = false,
  bridge = "workday",
  oneSpan = false,
  excludeBlackoutId,
  employee,
  onPick,
  onClose,
}: {
  title: string;
  initialDates?: string[];
  single?: boolean;
  workdayOnly?: boolean;
  bridge?: "workday" | "none";
  oneSpan?: boolean;
  excludeBlackoutId?: number;
  employee?: { id: number; name: string } | null;
  onPick: (dates: string[], spans: DateSpan[]) => void;
  onClose: () => void;
}) {
  const { toast } = useToast();
  const [range, setRange] = useState<{ start: string; end: string } | null>(null);
  const [selected, setSelected] = useState<string[]>(() => [...new Set(initialDates)].sort());

  const { data: events = [] } = useQuery({
    queryKey: ["calendarEvents", range],
    queryFn: () => calendarApi.events(range!.start, range!.end),
    enabled: !!range,
  });
  const { data: blackouts = [] } = useQuery({ queryKey: ["blackouts"], queryFn: policyRulesApi.blackouts });
  // 승인된 휴가는 캘린더 일정에 있고, 결재 대기는 캘린더에 없어 휴가 목록에서 그 직원 것만 따로 받는다
  const { data: pendingPage } = useQuery({
    queryKey: ["leaveList", "picker", employee?.id, range],
    queryFn: () =>
      leaveApi.search({ keyword: employee!.name, statuses: ["PENDING"], from: range!.start, to: range!.end, size: 100 }),
    enabled: !!employee && !!range,
  });

  const holidays = useMemo(
    () => new Set(events.filter((e) => e.source === "HOLIDAY").map((e) => e.start)),
    [events],
  );
  // 묶음 사이의 공휴일은 보이는 달 밖일 수도 있어 고른 날짜의 연도 공휴일을 따로 받는다
  const yearHolidays = useHolidaySet(selected);
  const isWorkday = useCallback(
    (date: string) => !isWeekend(date) && !holidays.has(date) && !yearHolidays.has(date),
    [holidays, yearHolidays],
  );
  const spans = useMemo(
    () => groupDates(selected, bridge === "workday" ? (d) => !isWorkday(d) : undefined),
    [selected, bridge, isWorkday],
  );

  const onDateClick = (arg: DateClickArg) => {
    const result = toggleDate(selected, arg.dateStr, single, workdayOnly ? isWorkday : () => true);
    if (result.ok) setSelected(result.dates);
    else toast({ title: result.message });
  };

  const pick = () => {
    if (oneSpan && spans.length > 1) {
      toast({ title: "이어진 기간 하나만 고를 수 있습니다.", variant: "destructive" });
      return;
    }
    onPick(selected, spans);
  };

  const onDatesSet = (arg: DatesSetArg) => {
    const start = localDateString(arg.start);
    const end = localDateString(new Date(arg.end.getTime() - 1));
    setRange((prev) => (prev?.start === start && prev.end === end ? prev : { start, end }));
  };

  // 휴가는 빼고 공휴일과 회사·팀 일정만(개인 일정도 뺀다)
  const calendarEvents: EventInput[] = events
    .filter((e) => e.source === "HOLIDAY" || (e.source === "ADMIN_EVENT" && e.scope !== "PERSONAL"))
    .map((e) => ({
      id: e.id,
      title: e.title,
      start: e.start,
      end: e.end,
      allDay: e.allDay,
      backgroundColor: e.colorHex,
      borderColor: e.colorHex,
      order: e.source === "HOLIDAY" ? 0 : e.scope === "COMPANY" ? 2 : 3,
    }));
  if (employee) {
    for (const e of events) {
      if (e.source !== "LEAVE_REQUEST" || e.employeeId !== employee.id) continue;
      calendarEvents.push({
        id: e.id,
        title: e.title,
        start: e.start,
        end: e.end,
        allDay: e.allDay,
        backgroundColor: e.colorHex,
        borderColor: e.colorHex,
        order: 4,
      });
    }
    // 결재 대기는 흐린 색으로(아직 확정 전)
    for (const r of pendingPage?.content ?? []) {
      if (r.employeeId !== employee.id) continue;
      calendarEvents.push({
        id: `P${r.id}`,
        title: `${r.employeeName} - ${r.leaveTypeName} (결재 대기)`,
        start: r.startDate,
        end: addDays(r.endDate, 1),
        allDay: true,
        backgroundColor: `${r.leaveTypeColor}80`,
        borderColor: r.leaveTypeColor,
        order: 5,
      });
    }
  }
  for (const b of blackouts) {
    if (b.id === excludeBlackoutId) continue;
    calendarEvents.push({
      id: `B${b.id}`,
      title: `연차 제한 · ${b.name}`,
      start: b.startDate,
      end: addDays(b.endDate, 1),
      allDay: true,
      backgroundColor: HOLIDAY_COLOR,
      borderColor: HOLIDAY_COLOR,
      order: 1,
    });
  }
  // 고른 날짜는 묶음째 배경색으로(사이에 낀 주말·공휴일도 한 신청이라 같이 칠한다)
  spans.forEach((s, i) => {
    calendarEvents.push({
      id: `selection-${i}`,
      start: s.startDate,
      end: addDays(s.endDate, 1),
      allDay: true,
      display: "background",
      backgroundColor: "#4f46e5",
    });
  });

  const hint = single
    ? "날짜 하나를 누르세요."
    : "날짜를 누를 때마다 넣고 뺍니다." + (oneSpan ? " 이어진 기간 하나만 고릅니다." : " 떨어진 날짜는 나눠서 따로 저장됩니다.");

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {employee ? `공휴일과 회사·팀 일정, ${employee.name}님의 휴가가 보입니다.` : "공휴일과 회사·팀 일정만 보입니다."}{" "}
            {hint}
          </DialogDescription>
        </DialogHeader>
        <FullCalendar
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          initialDate={selected[0] ?? todayString()}
          locale="ko"
          height={560}
          fixedWeekCount
          headerToolbar={{ left: "prev,next today", center: "title", right: "" }}
          buttonText={{ today: "오늘" }}
          events={calendarEvents}
          datesSet={onDatesSet}
          dateClick={onDateClick}
          dayCellClassNames={(arg) => (holidays.has(localDateString(arg.date)) ? ["fc-holiday"] : [])}
          // 작은 달력이라 칸 높이에 맞춰 접는다(줄 수로 자르면 일정 많은 주만 높아진다)
          dayMaxEvents
          eventOrder="order,start,-duration,title"
          moreLinkContent={(arg) => `+${arg.num}개 더보기`}
        />
        <DialogFooter className="items-center sm:justify-between">
          <p className="text-sm font-medium">
            {spans.length > 0 ? formatSpans(spans) : "고른 날짜 없음"}
            {spans.length > 1 && <span className="ml-2 text-xs text-muted-foreground">({spans.length}건)</span>}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button disabled={selected.length === 0} onClick={pick}>
              이 날짜로
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
