import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Search, X } from "lucide-react";
import { employeeApi } from "@/api/employees";
import { leaveApi } from "@/api/leave";
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
import type { Employee } from "@/types";

/**
 * 인사관리자·시스템 관리자의 휴가 직접 등록: 다른 직원의 휴가를 바로 승인 상태로 등록한다.
 * 지난 날짜도 가능하지만 시작일은 근무일이어야 하고, 블랙아웃·사전 신청 기한은 적용하지 않는다(서버 검사).
 */
export default function LeaveRegisterDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();

  const [employee, setEmployee] = useState<Employee | null>(null);
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [typeId, setTypeId] = useState("");
  const [specialRuleId, setSpecialRuleId] = useState("");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
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
  // 종일이 아닌 종류(반차·시간차)는 하루만 등록한다
  const isPartial = !!selectedType && selectedType.portion !== "FULL";
  const isHourly = selectedType?.portion === "HOURLY";
  const specialRules = selectedType?.specialRules ?? [];
  const needsRule = specialRules.length > 0;
  const endDate = isPartial ? start : end;

  useEffect(() => {
    setSpecialRuleId("");
    setHours("");
  }, [typeId]);

  const register = useMutation({
    mutationFn: () =>
      leaveApi.register({
        employeeId: employee!.id,
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate,
        reason: reason.trim() || undefined,
        hours: isHourly ? Number(hours) : undefined,
        specialRuleId: needsRule ? Number(specialRuleId) : undefined,
      }),
    onSuccess: () => {
      toast({ title: "휴가를 등록했습니다. 바로 승인 상태입니다.", variant: "success" });
      qc.invalidateQueries({ queryKey: ["calendarEvents"] });
      qc.invalidateQueries({ queryKey: ["calendarDay"] });
      qc.invalidateQueries({ queryKey: ["pendingApprovals"] });
      onClose();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const canSubmit =
    !!employee &&
    !!selectedType &&
    !!start &&
    !!endDate &&
    endDate >= start &&
    (!isHourly || !!hours) &&
    (!needsRule || !!specialRuleId) &&
    !register.isPending;

  const onSubmit = async () => {
    if (!employee || !selectedType) return;
    const period = start === endDate ? start : `${start} ~ ${endDate}`;
    const rule = needsRule ? specialRules.find((r) => String(r.id) === specialRuleId)?.name : null;
    const ok = await confirm({
      title: "휴가를 직접 등록할까요?",
      description:
        `${employee.name} · ${selectedType.name}${rule ? ` (${rule})` : ""} ${period}` +
        (isHourly ? ` ${hours}시간` : "") +
        "\n결재 없이 바로 승인되고, 본인과 담당 팀장에게 알림이 갑니다.",
      confirmText: "등록",
    });
    if (ok) register.mutate();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>휴가 직접 등록</DialogTitle>
          <DialogDescription>
            다른 직원의 휴가를 바로 승인 상태로 등록합니다. 지난 날짜도 등록할 수 있고, 시작일은 근무일이어야 합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label>직원</Label>
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
            <Label>휴가 종류</Label>
            <Select value={typeId} onValueChange={setTypeId}>
              <SelectTrigger>
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
              <Label>사유(규정)</Label>
              <Select value={specialRuleId} onValueChange={setSpecialRuleId}>
                <SelectTrigger>
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

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{isPartial ? "날짜" : "시작일"}</Label>
              <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>종료일</Label>
              <Input
                type="date"
                value={endDate}
                min={start || undefined}
                disabled={isPartial}
                onChange={(e) => setEnd(e.target.value)}
              />
            </div>
          </div>

          {isHourly && (
            <div className="space-y-2">
              <Label>시간</Label>
              <Select value={hours} onValueChange={setHours}>
                <SelectTrigger>
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
            <Label>사유 (선택)</Label>
            <Input
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
              placeholder="예: 구두 신청분 사후 등록"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          <Button onClick={onSubmit} disabled={!canSubmit}>
            {register.isPending && <Loader2 className="h-4 w-4 animate-spin" />} 등록
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
