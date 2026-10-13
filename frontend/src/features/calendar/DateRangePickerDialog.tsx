import { useCallback, useMemo, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DatesSetArg, EventInput } from "@fullcalendar/core";
import type { DateClickArg } from "@fullcalendar/interaction";
import { useQuery } from "@tanstack/react-query";
import { calendarApi } from "@/api/calendar";
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
import {
  addDays,
  formatDateWithWeekday,
  isWeekend,
  localDateString,
  nextSelection,
  todayString,
  type DateSelection,
} from "./useDateSelection";

/** 공휴일·연차 제한 막대 색(캘린더와 같음) */
const HOLIDAY_COLOR = "#ef4444";

/**
 * 일정만 보이는 달력에서 기간을 고르는 창(블랙아웃 등록·휴가 직접 등록).
 * 공휴일·회사 일정·팀 일정·연차 제한 기간만 보여 주고 휴가는 빼서 날짜를 고르기 쉽게 한다.
 * 고르는 규칙은 캘린더 휴가 신청과 같다: 첫 클릭 시작일, 두 번째 클릭 종료일, 세 번째 클릭은 새로 시작.
 *
 * @param single      하루만 고른다(반차·시간차 등)
 * @param workdayStart 시작일은 근무일이어야 한다(휴가). 블랙아웃은 아무 날이나
 * @param excludeBlackoutId 수정 중인 금지 기간은 달력에 그리지 않는다
 */
export default function DateRangePickerDialog({
  title,
  initialStart,
  initialEnd,
  single = false,
  workdayStart = false,
  excludeBlackoutId,
  onPick,
  onClose,
}: {
  title: string;
  initialStart?: string;
  initialEnd?: string;
  single?: boolean;
  workdayStart?: boolean;
  excludeBlackoutId?: number;
  onPick: (start: string, end: string) => void;
  onClose: () => void;
}) {
  const { toast } = useToast();
  const [range, setRange] = useState<{ start: string; end: string } | null>(null);
  const [selection, setSelection] = useState<DateSelection | null>(() =>
    initialStart ? { start: initialStart, end: !single && initialEnd && initialEnd !== initialStart ? initialEnd : null } : null,
  );

  const { data: events = [] } = useQuery({
    queryKey: ["calendarEvents", range],
    queryFn: () => calendarApi.events(range!.start, range!.end),
    enabled: !!range,
  });
  const { data: blackouts = [] } = useQuery({ queryKey: ["blackouts"], queryFn: policyRulesApi.blackouts });

  const holidays = useMemo(
    () => new Set(events.filter((e) => e.source === "HOLIDAY").map((e) => e.start)),
    [events],
  );
  const isWorkday = useCallback((date: string) => !isWeekend(date) && !holidays.has(date), [holidays]);

  const onDateClick = (arg: DateClickArg) => {
    const result = nextSelection(selection, arg.dateStr, single, workdayStart ? isWorkday : () => true);
    if (result.ok) setSelection(result.selection);
    else toast({ title: result.message });
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
  if (selection) {
    calendarEvents.push({
      id: "selection",
      start: selection.start,
      end: addDays(selection.end ?? selection.start, 1),
      allDay: true,
      display: "background",
      backgroundColor: "#4f46e5",
    });
  }

  const end = selection ? (selection.end ?? selection.start) : null;
  const hint = single
    ? "날짜 하나를 누르세요."
    : !selection
      ? "시작일을 누르세요."
      : selection.end === null
        ? "종료일을 누르세요. 이대로 고르면 이 하루만입니다."
        : "다른 날짜를 누르면 새로 고릅니다.";

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>공휴일과 회사·팀 일정만 보입니다. {hint}</DialogDescription>
        </DialogHeader>
        <FullCalendar
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          initialDate={initialStart ?? todayString()}
          locale="ko"
          height={560}
          fixedWeekCount
          headerToolbar={{ left: "prev,next today", center: "title", right: "" }}
          buttonText={{ today: "오늘" }}
          events={calendarEvents}
          datesSet={onDatesSet}
          dateClick={onDateClick}
          dayCellClassNames={(arg) => (holidays.has(localDateString(arg.date)) ? ["fc-holiday"] : [])}
          dayMaxEventRows={3}
          eventOrder="order,start,-duration,title"
          moreLinkContent={(arg) => `+${arg.num}개 더보기`}
        />
        <DialogFooter className="items-center sm:justify-between">
          <p className="text-sm font-medium">
            {selection
              ? formatDateWithWeekday(selection.start) +
                (end && end !== selection.start ? ` ~ ${formatDateWithWeekday(end)}` : "")
              : "고른 날짜 없음"}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button disabled={!selection} onClick={() => selection && onPick(selection.start, end ?? selection.start)}>
              이 날짜로
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
