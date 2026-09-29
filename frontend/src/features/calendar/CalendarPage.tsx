import { useMemo, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DatesSetArg, EventClickArg } from "@fullcalendar/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { calendarApi, type CalendarEventDto, type CalendarEventInput } from "@/api/calendar";
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
import { extractErrorMessage } from "@/api/client";
import { cn } from "@/lib/utils";

type ViewScope = "ALL" | "COMPANY" | "DEPARTMENT" | "PERSONAL";

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

  const filtered = useMemo(
    () => (view === "ALL" ? events : events.filter((e) => e.scope === view || e.source === "HOLIDAY")),
    [events, view],
  );

  const onDatesSet = (arg: DatesSetArg) => {
    const start = arg.start.toISOString().slice(0, 10);
    const end = arg.end.toISOString().slice(0, 10);
    setRange((prev) => (prev.start === start && prev.end === end ? prev : { start, end }));
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
    <div className="space-y-6">
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
          <FullCalendar
            plugins={[dayGridPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            locale="ko"
            height="auto"
            headerToolbar={{ left: "prev,next today", center: "title", right: "" }}
            buttonText={{ today: "오늘" }}
            events={filtered.map((e) => ({
              id: e.id,
              title: e.title,
              start: e.start,
              end: e.end,
              allDay: e.allDay,
              backgroundColor: e.colorHex,
              borderColor: e.colorHex,
              editable: false,
            }))}
            datesSet={onDatesSet}
            eventClick={onEventClick}
            dayMaxEvents={3}
          />
        </CardContent>
      </Card>

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
            <Button variant="destructive" onClick={onDelete}>
              삭제
            </Button>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button onClick={() => save.mutate()} disabled={!form.title.trim() || save.isPending}>
              저장
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
