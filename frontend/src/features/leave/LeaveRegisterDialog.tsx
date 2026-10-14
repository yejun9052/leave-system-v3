import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { invalidateLeaveData } from "@/lib/invalidate";
import { CalendarRange, Loader2, Search, X } from "lucide-react";
import { employeeApi } from "@/api/employees";
import { leaveApi } from "@/api/leave";
import { extractErrorCode, extractErrorMessage } from "@/api/client";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { formatDays } from "@/lib/leaveFormat";
import { formatSpans, type DateSpan } from "@/lib/dateSpans";
import { HALF_DAY_LABEL, type Employee, type HalfDayPart } from "@/types";
import DateRangePickerDialog from "@/features/calendar/DateRangePickerDialog";
import { HalfDayPartSelect } from "./LeaveRequestForm";

/**
 * 휴가 직접 등록 입력과 등록. 날짜(이어진 묶음)는 부르는 쪽이 정한다(등록 창의 달력, 캘린더 선택).
 * 묶음마다 등록 한 건(바로 승인)이고, 하나라도 걸리면 아무것도 등록하지 않는다.
 * 반차·시간차·반일 규정이면 isPartial 이 켜지고 첫 날짜 하나만 보낸다.
 */
export function useLeaveRegisterForm({ spans, onDone }: { spans: DateSpan[]; onDone: () => void }) {
  const start = spans[0]?.startDate ?? "";
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();

  const [employee, setEmployee] = useState<Employee | null>(null);
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [typeId, setTypeId] = useState("");
  const [specialRuleId, setSpecialRuleId] = useState("");
  const [halfDayPart, setHalfDayPart] = useState<HalfDayPart | "">("");
  const [hours, setHours] = useState("");
  const [reason, setReason] = useState("");

  // 입력이 잠깐 멈추면 검색한다
  useEffect(() => {
    const t = setTimeout(() => setSearch(keyword.trim()), 250);
    return () => clearTimeout(t);
  }, [keyword]);

  const { data: found, isFetching: searching } = useQuery({
    queryKey: ["registerEmployeeSearch", search],
    queryFn: () => employeeApi.search({ keyword: search, page: 0, size: 8 }),
    enabled: !employee && search.length > 0,
  });
  const candidates = (found?.content ?? []).filter((e) => e.status !== "RESIGNED");

  const { data: types = [] } = useQuery({ queryKey: ["leaveTypes", "active"], queryFn: leaveApi.activeTypes });
  const usableTypes = useMemo(() => types.filter((t) => t.policyEnabled), [types]);
  const selectedType = usableTypes.find((t) => String(t.id) === typeId);
  const specialRules = selectedType?.specialRules ?? [];
  const needsRule = specialRules.length > 0;
  // 1일 미만 규정(예: 생일 0.5일)은 오전·오후 반차로만 등록한다
  const selectedRule = specialRules.find((r) => String(r.id) === specialRuleId);
  const halfDayRule = !!selectedRule && selectedRule.days < 1;
  // 종일이 아닌 종류(반차·시간차)와 반일 규정은 하루만 등록한다
  const isPartial = (!!selectedType && selectedType.portion !== "FULL") || halfDayRule;
  const isHourly = selectedType?.portion === "HOURLY";
  const sendSpans: DateSpan[] = isPartial ? (start ? [{ startDate: start, endDate: start }] : []) : spans;

  useEffect(() => {
    setSpecialRuleId("");
    setHours("");
  }, [typeId]);
  useEffect(() => {
    setHalfDayPart("");
  }, [typeId, specialRuleId]);

  const register = useMutation({
    mutationFn: (limitAcknowledged: boolean) =>
      leaveApi.registerMany({
        employeeId: employee!.id,
        leaveTypeId: Number(typeId),
        spans: sendSpans,
        reason: reason.trim() || undefined,
        hours: isHourly ? Number(hours) : undefined,
        specialRuleId: needsRule ? Number(specialRuleId) : undefined,
        halfDayPart: halfDayRule && halfDayPart ? halfDayPart : undefined,
        limitAcknowledged: limitAcknowledged || undefined,
      }),
    onSuccess: (created) => {
      toast({
        title: created.length > 1
          ? `휴가 ${created.length}건을 등록했습니다. 바로 승인 상태입니다.`
          : "휴가를 등록했습니다. 바로 승인 상태입니다.",
        variant: "success",
      });
      invalidateLeaveData(qc);
      onDone();
    },
    onError: async (e) => {
      // 경조사 규정의 연간 사용 횟수(예: 생일 반차 연 1회)를 넘으면 막지 않고 경고를 확인받아 다시 보낸다
      if (extractErrorCode(e) === "LEAVE_SPECIAL_LIMIT_NOT_ACKNOWLEDGED") {
        const ok = await confirm({
          title: "연간 사용 횟수를 넘는 등록입니다",
          description: `${extractErrorMessage(e)}
그래도 등록할까요?`,
          confirmText: "그래도 등록",
          destructive: true,
        });
        if (ok) register.mutate(true);
        return;
      }
      toast({ title: extractErrorMessage(e), variant: "destructive" });
    },
  });

  const canSubmit =
    !!employee &&
    !!selectedType &&
    sendSpans.length > 0 &&
    (!isHourly || !!hours) &&
    (!needsRule || !!specialRuleId) &&
    (!halfDayRule || !!halfDayPart) &&
    !register.isPending;

  const submit = async () => {
    if (!employee || !selectedType) return;
    const period = formatSpans(sendSpans) + (sendSpans.length > 1 ? ` (${sendSpans.length}건)` : "");
    const rule = needsRule ? specialRules.find((r) => String(r.id) === specialRuleId)?.name : null;
    const ok = await confirm({
      title: "휴가를 직접 등록할까요?",
      description:
        `${employee.name} · ${selectedType.name}${rule ? ` (${rule})` : ""} ${period}` +
        (isHourly ? ` ${hours}시간` : "") +
        (halfDayRule && halfDayPart ? ` ${HALF_DAY_LABEL[halfDayPart]} 반차` : "") +
        "\n결재 없이 바로 승인되고, 본인과 담당 팀장에게 알림이 갑니다.",
      confirmText: "등록",
    });
    if (ok) register.mutate(false);
  };

  return {
    employee,
    setEmployee,
    keyword,
    setKeyword,
    search,
    setSearch,
    searching,
    candidates,
    usableTypes,
    typeId,
    setTypeId,
    needsRule,
    specialRules,
    specialRuleId,
    setSpecialRuleId,
    halfDayRule,
    halfDayPart,
    setHalfDayPart,
    isPartial,
    isHourly,
    sendSpans,
    hours,
    setHours,
    reason,
    setReason,
    canSubmit,
    pending: register.isPending,
    submit,
  };
}

export type LeaveRegisterFormState = ReturnType<typeof useLeaveRegisterForm>;

/** 직원·종류·규정·시간·사유 입력. 기간 칸은 dates 로 받아 종류 아래에 둔다 */
export function LeaveRegisterFields({ form, dates }: { form: LeaveRegisterFormState; dates: React.ReactNode }) {
  const {
    employee,
    setEmployee,
    keyword,
    setKeyword,
    search,
    setSearch,
    searching,
    candidates,
    usableTypes,
    typeId,
    setTypeId,
    needsRule,
    specialRules,
    specialRuleId,
    setSpecialRuleId,
    halfDayRule,
    halfDayPart,
    setHalfDayPart,
    isHourly,
    hours,
    setHours,
    reason,
    setReason,
  } = form;
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor={employee ? undefined : "leave-register-employee"}>직원</Label>
        {employee ? (
          <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
            <span>
              <span className="font-medium">{employee.name}</span>
              <span className="ml-2 text-xs text-muted-foreground">
                {employee.departmentName ?? "부서 없음"} · {employee.email}
              </span>
            </span>
            <Button
              size="sm"
              variant="ghost"
              aria-label="직원 다시 고르기"
              onClick={() => {
                setEmployee(null);
                setKeyword("");
                setSearch("");
              }}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        ) : (
          <div className="space-y-1">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="leave-register-employee"
                className="pl-9"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="이름 또는 이메일로 검색"
                autoFocus
              />
            </div>
            {search && (
              <ul className="max-h-48 divide-y overflow-y-auto rounded-md border">
                {searching && candidates.length === 0 ? (
                  <li className="flex justify-center py-3">
                    <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                  </li>
                ) : candidates.length === 0 ? (
                  <li className="px-3 py-2 text-sm text-muted-foreground">검색 결과가 없습니다.</li>
                ) : (
                  candidates.map((e) => (
                    <li key={e.id}>
                      <button
                        type="button"
                        className="w-full px-3 py-2 text-left text-sm hover:bg-accent"
                        onClick={() => setEmployee(e)}
                      >
                        <span className="font-medium">{e.name}</span>
                        <span className="ml-2 text-xs text-muted-foreground">
                          {e.departmentName ?? "부서 없음"} · {e.email}
                        </span>
                      </button>
                    </li>
                  ))
                )}
              </ul>
            )}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="leave-register-type">휴가 종류</Label>
        <Select value={typeId} onValueChange={setTypeId}>
          <SelectTrigger id="leave-register-type">
            <SelectValue placeholder="종류 선택" />
          </SelectTrigger>
          <SelectContent>
            {usableTypes.map((t) => (
              <SelectItem key={t.id} value={String(t.id)}>
                {t.name} ({formatDays(t.deductDays)}일 차감)
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {needsRule && (
        <div className="space-y-2">
          <Label htmlFor="leave-register-rule">사유(규정)</Label>
          <Select value={specialRuleId} onValueChange={setSpecialRuleId}>
            <SelectTrigger id="leave-register-rule">
              <SelectValue placeholder="규정 선택" />
            </SelectTrigger>
            <SelectContent>
              {specialRules.map((r) => (
                <SelectItem key={r.id} value={String(r.id)}>
                  {r.name} (최대 {formatDays(r.days)}일)
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
      {halfDayRule && <HalfDayPartSelect value={halfDayPart} onChange={setHalfDayPart} />}

      {dates}

      {isHourly && (
        <div className="space-y-2">
          <Label htmlFor="leave-register-hours">시간</Label>
          <Select value={hours} onValueChange={setHours}>
            <SelectTrigger id="leave-register-hours">
              <SelectValue placeholder="시간 선택 (1~3시간)" />
            </SelectTrigger>
            <SelectContent>
              {[1, 2, 3].map((h) => (
                <SelectItem key={h} value={String(h)}>
                  {h}시간
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor="leave-register-reason">사유 (선택)</Label>
        <Input
          id="leave-register-reason"
          value={reason}
          maxLength={500}
          onChange={(e) => setReason(e.target.value)}
          placeholder="예: 구두 신청분 사후 등록"
        />
      </div>
    </div>
  );
}

/**
 * 인사관리자·시스템 관리자의 휴가 직접 등록: 다른 직원의 휴가를 바로 승인 상태로 등록한다.
 * 지난 날짜도 가능하지만 근무일만 고르고, 블랙아웃·사전 신청 기한은 적용하지 않는다(서버 검사).
 * 날짜는 일정만 보이는 달력에서 누를 때마다 넣고 뺀다. 떨어진 날짜는 나눠서 여러 건으로 등록한다.
 * 캘린더 화면에서는 날짜를 눌러 등록하는 패널을 쓴다.
 */
export default function LeaveRegisterDialog({ onClose }: { onClose: () => void }) {
  const [spans, setSpans] = useState<DateSpan[]>([]);
  const [dates, setDates] = useState<string[]>([]);
  const [picking, setPicking] = useState(false);
  const form = useLeaveRegisterForm({ spans, onDone: onClose });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>휴가 직접 등록</DialogTitle>
          <DialogDescription>
            다른 직원의 휴가를 바로 승인 상태로 등록합니다. 지난 날짜도 등록할 수 있고, 근무일만 고를 수 있습니다.
          </DialogDescription>
        </DialogHeader>

        <LeaveRegisterFields
          form={form}
          dates={
            <div className="space-y-2">
              <Label>날짜</Label>
              <div className="flex items-center gap-2">
                <p className="flex-1 text-sm font-medium">
                  {form.sendSpans.length > 0 ? formatSpans(form.sendSpans) : "고른 날짜 없음"}
                </p>
                <Button type="button" size="sm" variant="outline" onClick={() => setPicking(true)}>
                  <CalendarRange className="h-4 w-4" /> 달력에서 고르기
                </Button>
              </div>
              {form.sendSpans.length > 1 && (
                <p className="text-xs text-primary">떨어진 날짜는 나눠서 {form.sendSpans.length}건으로 등록합니다.</p>
              )}
            </div>
          }
        />

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          <Button onClick={form.submit} disabled={!form.canSubmit}>
            {form.pending && <Loader2 className="h-4 w-4 animate-spin" />}
            {form.sendSpans.length > 1 ? `${form.sendSpans.length}건 등록` : "등록"}
          </Button>
        </DialogFooter>
      </DialogContent>
      {picking && (
        <DateRangePickerDialog
          title={form.isPartial ? "등록할 날짜 고르기" : "등록할 날짜 고르기 (여러 날 가능)"}
          initialDates={form.isPartial ? dates.slice(0, 1) : dates}
          single={form.isPartial}
          workdayOnly
          bridge="workday"
          employee={form.employee}
          onPick={(picked, pickedSpans) => {
            setDates(picked);
            setSpans(pickedSpans);
            setPicking(false);
          }}
          onClose={() => setPicking(false)}
        />
      )}
    </Dialog>
  );
}
