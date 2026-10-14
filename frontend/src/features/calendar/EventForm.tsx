import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { calendarApi, type CalendarEventDto, type CalendarEventInput, type EventScopeOption } from "@/api/calendar";
import { departmentApi } from "@/api/departments";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectTreeItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { flattenDepartments } from "@/lib/departmentTree";
import EntryPanel, { SelectionPeriod } from "./EntryPanel";
import { todayString } from "./useDateSelection";
import { formatSpans, type DateSpan } from "@/lib/dateSpans";

/** 일정 범위 선택지의 Select 값: "COMPANY" 또는 "DEPARTMENT:3" */
function scopeKey(scope: string, departmentId: number | null | undefined): string {
  return scope === "DEPARTMENT" ? `DEPARTMENT:${departmentId ?? ""}` : scope;
}

/**
 * 회사·팀 일정 입력(제목·범위·색상)과 저장. 날짜는 부르는 쪽이 정한다(수정 창의 날짜 칸, 캘린더 선택).
 * 캘린더에서 여러 날짜를 고르면 spans(달력 날짜가 바로 이어진 묶음)마다 일정 하나를 한 번에 추가한다.
 *
 * @param event 수정할 일정. 없으면 추가
 */
function useEventForm({
  event,
  startDate,
  endDate,
  spans,
  onSaved,
}: {
  event: CalendarEventDto | null;
  startDate: string;
  endDate: string;
  spans?: DateSpan[];
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const isEdit = !!event;
  const [title, setTitle] = useState(event?.title ?? "");
  const [colorHex, setColorHex] = useState(event?.colorHex ?? "#4f46e5");

  // 범위는 드롭다운 하나로 고른다(관리자: 전체 일정 + 모든 팀, 팀장: 맡은 팀). 기본값은 "선택"(미선택),
  // 수정이면 그 일정의 범위가 선택된 상태. 선택지에 없는 기존 범위(개인 일정 등)는 다시 고르게 한다.
  const { data: scopeOptions, isLoading: scopesLoading } = useQuery({
    queryKey: ["eventScopes"],
    queryFn: calendarApi.eventScopes,
  });
  const [selectedKey, setSelectedKey] = useState(() => (event ? scopeKey(event.scope, event.departmentId) : ""));
  const selectedScope: EventScopeOption | undefined = scopeOptions?.find(
    (o) => scopeKey(o.scope, o.departmentId) === selectedKey,
  );
  const previousScopeMissing = isEdit && !!scopeOptions && !selectedScope;
  // 부서 선택지는 서버가 트리 순서로 준다. 부서 트리로 깊이를 붙여 하위 부서를 들여쓴다
  // (팀장은 맡은 부서부터 보이므로 선택지 중 가장 얕은 부서를 0단계로 맞춘다)
  const { data: departmentTree = [] } = useQuery({
    queryKey: ["departments", "tree"],
    queryFn: departmentApi.tree,
  });
  const scopeDepth = useMemo(() => {
    const depthById = new Map(flattenDepartments(departmentTree).map((d) => [d.id, d.depth]));
    const depths = (scopeOptions ?? [])
      .filter((o) => o.departmentId != null)
      .map((o) => depthById.get(o.departmentId!) ?? 0);
    const base = depths.length > 0 ? Math.min(...depths) : 0;
    return (o: EventScopeOption) =>
      o.departmentId != null ? (depthById.get(o.departmentId) ?? base) - base : 0;
  }, [departmentTree, scopeOptions]);

  const body = (): CalendarEventInput => ({
    title: title.trim(),
    startDate,
    endDate,
    allDay: event?.allDay ?? true,
    scope: selectedScope?.scope === "DEPARTMENT" ? "DEPARTMENT" : "COMPANY",
    departmentId: selectedScope?.departmentId ?? null,
    colorHex,
  });

  const save = useMutation({
    mutationFn: async () => {
      if (isEdit && event) {
        await calendarApi.update(Number(event.id.slice(1)), body());
      } else if (spans) {
        const b = body();
        await calendarApi.createMany({
          title: b.title,
          allDay: b.allDay,
          scope: b.scope,
          departmentId: b.departmentId,
          colorHex: b.colorHex,
          spans,
        });
      } else {
        await calendarApi.create(body());
      }
    },
    onSuccess: () => {
      toast({ title: "저장되었습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const submit = async () => {
    const period = spans ? formatSpans(spans) : `${startDate}${endDate !== startDate ? ` ~ ${endDate}` : ""}`;
    const many = !isEdit && !!spans && spans.length > 1;
    const ok = await confirm({
      title: isEdit ? "일정을 수정할까요?" : many ? `일정 ${spans!.length}개를 추가할까요?` : "일정을 추가할까요?",
      description: `${title.trim()} (${period}, ${selectedScope?.label ?? ""})`,
      confirmText: isEdit ? "수정" : "추가",
    });
    if (ok) save.mutate();
  };

  return {
    isEdit,
    title,
    setTitle,
    colorHex,
    setColorHex,
    scopeOptions,
    scopesLoading,
    selectedKey,
    setSelectedKey,
    selectedScope,
    previousScopeMissing,
    scopeDepth,
    canSubmit:
      !!title.trim() &&
      !!selectedScope &&
      (spans ? spans.length > 0 : !!startDate && endDate >= startDate) &&
      !save.isPending,
    submit,
  };
}

type EventFormState = ReturnType<typeof useEventForm>;

/** 제목, (기간), 범위·색상 */
function EventFields({ form, dates }: { form: EventFormState; dates: React.ReactNode }) {
  const { scopeOptions, selectedScope, selectedKey } = form;
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="event-title">제목</Label>
        <Input id="event-title" value={form.title} onChange={(e) => form.setTitle(e.target.value)} />
      </div>
      {dates}
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <Label htmlFor="event-scope">범위</Label>
          <Select
            value={selectedScope ? selectedKey : ""}
            onValueChange={form.setSelectedKey}
            disabled={form.scopesLoading || scopeOptions?.length === 0}
          >
            <SelectTrigger id="event-scope">
              <SelectValue placeholder="선택" />
            </SelectTrigger>
            <SelectContent>
              {scopeOptions?.map((o) => {
                const key = scopeKey(o.scope, o.departmentId);
                return (
                  <SelectTreeItem key={key} value={key} depth={form.scopeDepth(o)}>
                    {o.label}
                  </SelectTreeItem>
                );
              })}
            </SelectContent>
          </Select>
          {scopeOptions?.length === 0 && <p className="text-xs text-destructive">등록할 수 있는 범위가 없습니다.</p>}
          {form.previousScopeMissing && (scopeOptions?.length ?? 0) > 0 && (
            <p className="text-xs text-muted-foreground">기존 범위는 고를 수 없는 범위입니다. 다시 선택해 주세요.</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="event-color">색상</Label>
          <Input id="event-color" type="color" value={form.colorHex} onChange={(e) => form.setColorHex(e.target.value)} />
        </div>
      </div>
    </div>
  );
}

/** 일정 수정(일정 막대 클릭)·추가 창 */
export function EventDialog({
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
  const confirm = useConfirm();
  const [startDate, setStartDate] = useState(event?.start ?? todayString());
  // 종일 일정의 끝은 다음 날(배타적)로 저장돼 있어 수정 칸에는 하루 앞을 보여 준다
  const [endDate, setEndDate] = useState(
    event && event.allDay
      ? new Date(new Date(event.end).getTime() - 86400000).toISOString().slice(0, 10)
      : event?.end ?? todayString(),
  );
  const form = useEventForm({ event, startDate, endDate, onSaved });

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
          <DialogTitle>{form.isEdit ? "일정 수정" : "일정 추가"}</DialogTitle>
          <DialogDescription className="sr-only">제목·기간·범위·색상을 정해 캘린더 일정을 저장합니다.</DialogDescription>
        </DialogHeader>
        <EventFields
          form={form}
          dates={
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="event-start">시작일</Label>
                <Input id="event-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="event-end">종료일</Label>
                <Input id="event-end" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </div>
          }
        />
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
            <Button onClick={form.submit} disabled={!form.canSubmit}>
              저장
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** 캘린더에서 고른 날짜로 회사·팀 일정 추가(팀장·인사관리자·시스템 관리자). 바로 이어진 날짜끼리 일정 하나 */
export function EventEntryPanel({
  spans,
  variant = "floating",
  hidden,
  onChangeDates,
  onClose,
  onSaved,
  heading,
}: {
  spans: DateSpan[];
  variant?: "floating" | "sheet";
  hidden?: boolean;
  onChangeDates?: () => void;
  onClose: () => void;
  onSaved: () => void;
  heading?: React.ReactNode;
}) {
  const form = useEventForm({
    event: null,
    startDate: spans[0]?.startDate ?? "",
    endDate: spans[spans.length - 1]?.endDate ?? "",
    spans,
    onSaved,
  });
  return (
    <EntryPanel
      title="일정 추가"
      heading={heading}
      variant={variant}
      hidden={hidden}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={form.submit} disabled={!form.canSubmit}>
            {spans.length > 1 ? `${spans.length}개 추가` : "추가"}
          </Button>
        </>
      }
    >
      <EventFields
        form={form}
        dates={
          <SelectionPeriod
            spans={spans}
            sheet={variant === "sheet"}
            splitNote={`떨어진 날짜는 나눠서 일정 ${spans.length}개로 추가합니다.`}
            onChangeDates={onChangeDates}
          />
        }
      />
    </EntryPanel>
  );
}
