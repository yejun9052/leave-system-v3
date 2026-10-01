import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DatesSetArg, EventClickArg, EventInput } from "@fullcalendar/core";
import type { DateClickArg } from "@fullcalendar/interaction";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { calendarApi, type CalendarEventDto, type CalendarEventInput } from "@/api/calendar";
import { policyRulesApi } from "@/api/policy";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { cn } from "@/lib/utils";
import { useMediaQuery } from "@/lib/useMediaQuery";
import LeaveEntryPanel from "./LeaveEntryPanel";
import DayContextMenu, { type DayMenuTarget } from "./DayContextMenu";
import DayDetailDialog from "./DayDetailDialog";
import MobileDaySummary from "./MobileDaySummary";
import MobileMonthView, { type DayMark } from "./MobileMonthView";
import {
  PARTIAL_ONE_DAY_MESSAGE,
  addDays,
  formatDateWithWeekday,
  isWeekend,
  localDateString,
  monthGridDates,
  todayString,
  useDateSelection,
  type DateSelection,
} from "./useDateSelection";

type ViewScope = "ALL" | "COMPANY" | "DEPARTMENT" | "PERSONAL";

/** 공휴일과 같은 빨간색(서버가 공휴일 일정에 주는 색) */
const HOLIDAY_COLOR = "#ef4444";

const VIEW_TABS: { key: ViewScope; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "COMPANY", label: "전사" },
  { key: "DEPARTMENT", label: "부서" },
  { key: "PERSONAL", label: "개인" },
];

export default function CalendarPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const canManage = useAuthStore((s) => s.hasAnyRole("TEAM_LEAD", "HR_ADMIN", "SUPER_ADMIN"));
  // 관리 전용 계정은 직원이 아니므로 휴가를 신청하지 않는다
  const canApply = useAuthStore((s) => !!s.user && !s.user.systemAccount);
  const [range, setRange] = useState<{ start: string; end: string }>(() => {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString().slice(0, 10);
    const end = new Date(now.getFullYear(), now.getMonth() + 2, 0).toISOString().slice(0, 10);
    return { start, end };
  });
  const [view, setView] = useState<ViewScope>("ALL");
  const [editing, setEditing] = useState<CalendarEventDto | null>(null);
  const [creating, setCreating] = useState(false);

  const { data: events = [] } = useQuery({
    queryKey: ["calendarEvents", range],
    queryFn: () => calendarApi.events(range.start, range.end),
  });

  const { data: blackouts = [] } = useQuery({ queryKey: ["blackouts"], queryFn: policyRulesApi.blackouts });

  const filtered = useMemo(
    () => (view === "ALL" ? events : events.filter((e) => e.scope === view || e.source === "HOLIDAY")),
    [events, view],
  );

  // 신청 시작일 검사용 공휴일(보이는 달 범위만 불러오지만, 누를 수 있는 날짜도 그 범위 안이다)
  const holidays = useMemo(
    () => new Set(events.filter((e) => e.source === "HOLIDAY").map((e) => e.start)),
    [events],
  );
  const isWorkday = useCallback((date: string) => !isWeekend(date) && !holidays.has(date), [holidays]);
  const notify = useCallback((message: string) => toast({ title: message }), [toast]);
  const selector = useDateSelection(isWorkday, notify);
  const { selection } = selector;
  const [panelOpen, setPanelOpen] = useState(false);
  // 패널에서 반차·반반차·시간차를 고른 상태면 누를 때마다 그 하루만 선택한다
  const [partial, setPartial] = useState(false);

  // 모바일(폭 640px 미만): FullCalendar 대신 전용 월 격자. 탭은 날짜 선택 + 아래 요약,
  // 신청 기간은 신청 시트의 "날짜 바꾸기" 선택 모드에서 고른다
  const isMobile = useMediaQuery("(max-width: 639px)");
  const [viewMonth, setViewMonth] = useState(() => todayString().slice(0, 7));
  const [focused, setFocused] = useState(todayString);
  const [picking, setPicking] = useState(false);
  const pickSnapshot = useRef<DateSelection | null>(null);
  // 선택 모드에 들어온 뒤 아직 한 번도 누르지 않았으면 첫 탭을 시작일로 본다
  const [pickFresh, setPickFresh] = useState(false);
  // 모바일 돋보기: 불러온 달 일정에서 이름(제목)으로 거른다(화면에서만)
  const [query, setQuery] = useState("");

  /** 신청 기간 고르기(데스크톱 날짜 클릭, 모바일 선택 모드 탭) */
  const pickDate = (date: string) => {
    if (!canApply) return;
    if (picking && pickFresh) {
      if (selector.startAt(date)) setPickFresh(false);
      return;
    }
    if (selector.pick(date, partial) && !picking) setPanelOpen(true);
  };

  const onDateClick = (arg: DateClickArg) => pickDate(arg.dateStr);

  // 모바일은 FullCalendar 가 없어 보고 있는 달의 격자 범위로 일정을 불러온다
  useEffect(() => {
    if (!isMobile) return;
    const grid = monthGridDates(viewMonth);
    const next = { start: grid[0], end: addDays(grid[grid.length - 1], 1) };
    setRange((prev) => (prev.start === next.start && prev.end === next.end ? prev : next));
  }, [isMobile, viewMonth]);

  /** 모바일 달 이동: 고른 날이 그 달이 아니면 그 달의 오늘(없으면 1일)을 고른다 */
  const changeMobileMonth = (month: string) => {
    setViewMonth(month);
    setFocused((prev) => {
      if (prev.slice(0, 7) === month) return prev;
      const today = todayString();
      return today.slice(0, 7) === month ? today : `${month}-01`;
    });
  };

  /** 모바일 칸 탭: 다른 달 날짜면 그 달로 이동하며 선택, 선택 모드면 신청 기간을 고른다 */
  const onMobileSelect = (date: string) => {
    if (date.slice(0, 7) !== viewMonth) setViewMonth(date.slice(0, 7));
    if (picking) pickDate(date);
    else setFocused(date);
  };

  const goToday = () => {
    const today = todayString();
    setViewMonth(today.slice(0, 7));
    setFocused(today);
  };

  // 날짜 우클릭 메뉴와 날짜 상세 창
  const [menu, setMenu] = useState<DayMenuTarget | null>(null);
  const [detailDate, setDetailDate] = useState<string | null>(null);

  /**
   * FullCalendar 에는 우클릭 콜백이 없어 달력 영역의 contextmenu 에서 날짜 칸(data-date)을 찾는다.
   * 여러 날에 걸친 막대는 시작 칸 안에 그려지므로, 막대 위에서는 포인터 바로 아래 날짜 칸을 쓴다.
   */
  const dateAtPoint = (target: EventTarget | null, x: number, y: number): string | null => {
    if (!(target instanceof Element)) return null;
    if (target.closest(".fc-event:not(.fc-bg-event)")) {
      const cell = document.elementsFromPoint(x, y).find((el) => el.matches(".fc-daygrid-day[data-date]"));
      if (cell) return cell.getAttribute("data-date");
    }
    return target.closest("[data-date]")?.getAttribute("data-date") ?? null;
  };

  const onContextMenu = (e: React.MouseEvent) => {
    const date = dateAtPoint(e.target, e.clientX, e.clientY);
    if (!date) return; // 날짜 칸이 아니면(툴바 등) 브라우저 기본 메뉴
    e.preventDefault();
    setMenu({ date, x: e.clientX, y: e.clientY });
  };

  /** 그 날짜를 시작일로 선택을 새로 시작하고 패널을 연다. */
  const applyFrom = (date: string) => {
    setMenu(null);
    setDetailDate(null);
    if (selector.startAt(date)) setPanelOpen(true);
  };

  const onPartialChange = (next: boolean) => {
    setPartial(next);
    if (next && selector.collapseToStart()) notify(PARTIAL_ONE_DAY_MESSAGE);
  };

  // 모바일 날짜 선택 모드: 들어가기 전 선택을 기억해 두고 취소하면 되돌린다
  const startPicking = () => {
    pickSnapshot.current = selection;
    setPickFresh(true);
    setPicking(true);
  };
  const finishPicking = () => setPicking(false);
  const cancelPicking = () => {
    selector.setSelection(pickSnapshot.current);
    setPicking(false);
  };

  const closePanel = () => {
    setPicking(false);
    setPanelOpen(false);
    setPartial(false);
    selector.clear();
  };

  const onLeaveSaved = () => {
    qc.invalidateQueries({ queryKey: ["myRequests"] });
    qc.invalidateQueries({ queryKey: ["myBalance"] });
    qc.invalidateQueries({ queryKey: ["calendarEvents"] });
    qc.invalidateQueries({ queryKey: ["calendarDay"] });
    closePanel();
  };

  const calendarEvents: EventInput[] = filtered.map((e) => ({
    id: e.id,
    title: e.title,
    start: e.start,
    end: e.end,
    allDay: e.allDay,
    backgroundColor: e.colorHex,
    borderColor: e.colorHex,
    editable: false,
  }));
  // 블랙아웃(연차 사용 제한) 기간은 공휴일처럼 빨간 막대로, 보기 탭과 관계없이 항상 표시한다
  for (const b of blackouts) {
    calendarEvents.push({
      id: `B${b.id}`,
      title: `연차 제한 · ${b.name}`,
      start: b.startDate,
      end: addDays(b.endDate, 1),
      allDay: true,
      backgroundColor: HOLIDAY_COLOR,
      borderColor: HOLIDAY_COLOR,
      editable: false,
    });
  }
  if (selection) {
    // 신청할 기간을 배경색으로 강조(배경 이벤트는 날짜 클릭을 막지 않는다)
    calendarEvents.push({
      id: "selection",
      start: selection.start,
      end: addDays(selection.end ?? selection.start, 1),
      allDay: true,
      display: "background",
      backgroundColor: "#4f46e5",
    });
  }

  // 모바일 월 격자: 날짜별 점(공휴일·연차 제한·휴가·회사 일정)과 공휴일 이름
  const holidayNames = useMemo(() => {
    const map = new Map<string, string>();
    for (const e of events) {
      if (e.source === "HOLIDAY") map.set(e.start, map.has(e.start) ? `${map.get(e.start)}, ${e.title}` : e.title);
    }
    return map;
  }, [events]);

  const marks = useMemo(() => {
    const map = new Map<string, DayMark[]>();
    if (!isMobile) return map;
    const grid = monthGridDates(viewMonth);
    const first = grid[0];
    const last = grid[grid.length - 1];
    const add = (start: string, endInclusive: string, mark: DayMark) => {
      for (let d = start < first ? first : start; d <= endInclusive && d <= last; d = addDays(d, 1)) {
        map.set(d, [...(map.get(d) ?? []), mark]);
      }
    };
    const q = query.trim();
    for (const e of events) {
      const kind: DayMark["kind"] =
        e.source === "HOLIDAY" ? "HOLIDAY" : e.source === "LEAVE_REQUEST" ? "LEAVE" : "EVENT";
      if (kind !== "HOLIDAY" && q && !e.title.includes(q)) continue;
      // 종일 일정의 end 는 다음 날(배타적)로 온다
      add(e.start, e.allDay ? addDays(e.end, -1) : e.end, { kind, title: e.title });
    }
    for (const b of blackouts) add(b.startDate, b.endDate, { kind: "BLACKOUT", title: `연차 제한 · ${b.name}` });
    const order: DayMark["kind"][] = ["HOLIDAY", "BLACKOUT", "LEAVE", "EVENT"];
    for (const list of map.values()) list.sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind));
    return map;
  }, [isMobile, viewMonth, events, blackouts, query]);

  const focusedBlackouts = blackouts
    .filter((b) => b.startDate <= focused && b.endDate >= focused)
    .map((b) => b.name);

  /** 모바일 요약 카드 탭: 수정할 수 있는 회사 일정은 데스크톱처럼 수정 창, 그 밖은 그날 상세 */
  const onOpenSummaryItem = (eventId: string | null) => {
    const ev = eventId ? events.find((e) => e.id === eventId) : undefined;
    if (ev?.editable) setEditing(ev);
    else setDetailDate(focused);
  };

  const onDatesSet = (arg: DatesSetArg) => {
    const start = arg.start.toISOString().slice(0, 10);
    const end = arg.end.toISOString().slice(0, 10);
    setRange((prev) => (prev.start === start && prev.end === end ? prev : { start, end }));
    // 모바일 요약 카드: 다른 달로 넘기면 그 달의 오늘(없으면 1일)을 보여 준다
    const month = localDateString(arg.view.currentStart).slice(0, 7);
    setViewMonth(month);
    setFocused((prev) => {
      if (prev.slice(0, 7) === month) return prev;
      const today = todayString();
      return today.slice(0, 7) === month ? today : `${month}-01`;
    });
  };

  const onEventClick = (arg: EventClickArg) => {
    const ev = events.find((e) => e.id === arg.event.id);
    if (ev && ev.editable) setEditing(ev);
  };

  const invalidate = () => qc.invalidateQueries({ queryKey: ["calendarEvents"] });

  const remove = useMutation({
    mutationFn: (id: number) => calendarApi.remove(id),
    onSuccess: () => {
      toast({ title: "일정이 삭제되었습니다.", variant: "success" });
      setEditing(null);
      invalidate();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    <div className={cn("space-y-6", isMobile && panelOpen && "pb-48")}>
      {isMobile ? (
        <>
          <MobileMonthView
            month={viewMonth}
            focused={focused}
            picking={picking}
            pickRange={
              picking && selection && !pickFresh
                ? { start: selection.start, end: selection.end ?? selection.start }
                : null
            }
            marks={marks}
            holidayNames={holidayNames}
            query={query}
            onQueryChange={setQuery}
            onMonthChange={changeMobileMonth}
            onToday={goToday}
            onSelectDate={onMobileSelect}
            onLongPress={(date, x, y) => setMenu({ date, x, y })}
          />
          <MobileDaySummary
            date={focused}
            canApply={canApply}
            query={query}
            blackoutNames={focusedBlackouts}
            onApply={applyFrom}
            onOpenItem={onOpenSummaryItem}
          />
        </>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold">캘린더</h1>
              <p className="text-sm text-muted-foreground">전사 휴가 현황과 일정을 확인합니다.</p>
            </div>
            {canManage && (
              <Button onClick={() => setCreating(true)}>
                <Plus className="h-4 w-4" /> 일정 추가
              </Button>
            )}
          </div>

          <div className="flex gap-1 rounded-lg bg-muted p-1 w-fit">
            {VIEW_TABS.map((t) => (
              <button
                key={t.key}
                onClick={() => setView(t.key)}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  view === t.key ? "bg-background shadow-sm" : "text-muted-foreground",
                )}
              >
                {t.label}
              </button>
            ))}
          </div>

          <Card>
            <CardContent className="p-2 sm:p-4">
              <div onContextMenu={onContextMenu}>
                <FullCalendar
                  plugins={[dayGridPlugin, interactionPlugin]}
                  initialView="dayGridMonth"
                  initialDate={`${viewMonth}-01`}
                  locale="ko"
                  height="auto"
                  headerToolbar={{ left: "prev,next today", center: "title", right: "" }}
                  buttonText={{ today: "오늘" }}
                  events={calendarEvents}
                  datesSet={onDatesSet}
                  eventClick={onEventClick}
                  dateClick={onDateClick}
                  dayMaxEvents={3}
                />
              </div>
            </CardContent>
          </Card>
        </>
      )}

      {menu && (
        <DayContextMenu
          key={`${menu.date}-${menu.x}-${menu.y}`}
          target={menu}
          canApply={canApply}
          onApply={applyFrom}
          onDetail={(date) => {
            setMenu(null);
            setDetailDate(date);
          }}
          onClose={() => setMenu(null)}
        />
      )}

      {detailDate && (
        <DayDetailDialog
          date={detailDate}
          canApply={canApply}
          onApply={applyFrom}
          onClose={() => setDetailDate(null)}
        />
      )}

      {panelOpen && selection && (
        <LeaveEntryPanel
          selection={selection}
          variant={isMobile ? "sheet" : "floating"}
          hidden={picking}
          onChangeDates={isMobile ? startPicking : undefined}
          onClose={closePanel}
          onSaved={onLeaveSaved}
          onPartialChange={onPartialChange}
        />
      )}

      {picking && selection && (
        <div className="fixed inset-x-0 bottom-0 z-40 space-y-2 border-t bg-background p-4 shadow-xl">
          <p className="text-sm font-medium">
            {pickFresh
              ? "달력에서 시작일을 누르세요"
              : formatDateWithWeekday(selection.start) +
                (!partial && selection.end !== null && selection.end !== selection.start
                  ? ` ~ ${formatDateWithWeekday(selection.end)}`
                  : "")}
          </p>
          <p className="text-xs text-muted-foreground">
            {partial
              ? "반차·반반차·시간차는 날짜 하나를 누르세요."
              : "첫 탭은 시작일, 두 번째 탭은 종료일입니다. 한 번 더 누르면 새로 고릅니다."}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={cancelPicking}>
              취소
            </Button>
            <Button onClick={finishPicking}>완료</Button>
          </div>
        </div>
      )}

      {(creating || editing) && (
        <EventDialog
          event={editing}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={() => {
            setCreating(false);
            setEditing(null);
            invalidate();
          }}
          onDelete={editing ? () => remove.mutate(Number(editing.id.slice(1))) : undefined}
        />
      )}
    </div>
  );
}

function EventDialog({
  event,
  onClose,
  onSaved,
  onDelete,
}: {
  event: CalendarEventDto | null;
  onClose: () => void;
  onSaved: () => void;
  onDelete?: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const isEdit = !!event;
  const [form, setForm] = useState<CalendarEventInput>({
    title: event?.title ?? "",
    startDate: event?.start ?? new Date().toISOString().slice(0, 10),
    // stored end is exclusive for allDay; convert back to inclusive for editing
    endDate:
      event && event.allDay
        ? new Date(new Date(event.end).getTime() - 86400000).toISOString().slice(0, 10)
        : event?.end ?? new Date().toISOString().slice(0, 10),
    allDay: event?.allDay ?? true,
    scope: (event?.scope as "COMPANY" | "DEPARTMENT") ?? "COMPANY",
    departmentId: event?.departmentId ?? null,
    colorHex: event?.colorHex ?? "#4f46e5",
  });
  const set = <K extends keyof CalendarEventInput>(k: K, v: CalendarEventInput[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const save = useMutation({
    mutationFn: () =>
      isEdit && event
        ? calendarApi.update(Number(event.id.slice(1)), form)
        : calendarApi.create(form),
    onSuccess: () => {
      toast({ title: "저장되었습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onSave = async () => {
    const ok = await confirm({
      title: isEdit ? "일정을 수정할까요?" : "일정을 추가할까요?",
      description: `${form.title.trim()} (${form.startDate}${form.endDate !== form.startDate ? ` ~ ${form.endDate}` : ""}, ${form.scope === "COMPANY" ? "전사" : "부서"})`,
      confirmText: isEdit ? "수정" : "추가",
    });
    if (ok) save.mutate();
  };

  const onDeleteClick = async () => {
    const ok = await confirm({
      title: "일정을 삭제할까요?",
      description: form.title.trim(),
      confirmText: "삭제",
      destructive: true,
    });
    if (ok) onDelete?.();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "일정 수정" : "일정 추가"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>제목</Label>
            <Input value={form.title} onChange={(e) => set("title", e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>시작일</Label>
              <Input type="date" value={form.startDate} onChange={(e) => set("startDate", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>종료일</Label>
              <Input type="date" value={form.endDate} onChange={(e) => set("endDate", e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>범위</Label>
              <Select value={form.scope} onValueChange={(v) => set("scope", v as "COMPANY" | "DEPARTMENT")}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="COMPANY">전사</SelectItem>
                  <SelectItem value="DEPARTMENT">부서</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>색상</Label>
              <Input type="color" value={form.colorHex} onChange={(e) => set("colorHex", e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter className="sm:justify-between">
          {onDelete ? (
            <Button variant="destructive" onClick={onDeleteClick}>
              삭제
            </Button>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button onClick={onSave} disabled={!form.title.trim() || save.isPending}>
              저장
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
