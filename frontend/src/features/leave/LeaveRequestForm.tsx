import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { leaveApi, type LeaveRequestCreate } from "@/api/leave";
import type { LeaveType } from "@/types";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { extractErrorMessage } from "@/api/client";
import { formatDays } from "@/lib/leaveFormat";
import { cn } from "@/lib/utils";

/** 캘린더 패널에서 마지막으로 고른 휴가 종류(id 만 저장) */
const LAST_TYPE_KEY = "annualLeave.lastLeaveTypeId";

function readLastTypeId(): string {
  try {
    return localStorage.getItem(LAST_TYPE_KEY) ?? "";
  } catch {
    return "";
  }
}

function writeLastTypeId(id: string) {
  try {
    localStorage.setItem(LAST_TYPE_KEY, id);
  } catch {
    // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 기억하지 않는다
  }
}

/** 마지막 글자에 받침이 있는지(한글이 아니면 받침 없음으로 본다). */
function hasFinalConsonant(word: string): boolean {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}
/** "로/으로" 조사(받침이 있고 ㄹ 받침이 아니면 "으로"). */
const roParticle = (word: string) => {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return hasFinalConsonant(word) && code % 28 !== 8 ? "으로" : "로";
};
const topicParticle = (word: string) => (hasFinalConsonant(word) ? "은" : "는");
const objectParticle = (word: string) => (hasFinalConsonant(word) ? "을" : "를");

export interface LeaveRequestFormOptions {
  start: string;
  end: string;
  /** 마지막으로 고른 휴가 종류를 브라우저에 기억해 기본값으로 쓴다(캘린더 패널) */
  rememberType?: boolean;
  /** warning: 신청은 됐지만 결재할 인사관리자가 없는 경우 등의 안내(없으면 null) */
  onSaved: (warning?: string | null) => void;
}

export type LeaveRequestFormState = ReturnType<typeof useLeaveRequestForm>;

/**
 * 휴가 신청 입력·검증 상태. "내 휴가" 신청 다이얼로그와 캘린더 신청 패널이 함께 쓴다.
 * 날짜(start/end)는 호출하는 쪽이 관리하고, 부분 휴가(반차·시간차)면 종료일을 시작일로 본다.
 */
export function useLeaveRequestForm({ start, end, rememberType, onSaved }: LeaveRequestFormOptions) {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data: types = [] } = useQuery({ queryKey: ["leaveTypes", "active"], queryFn: leaveApi.activeTypes });
  const [typeId, setTypeIdState] = useState<string>(() => (rememberType ? readLastTypeId() : ""));
  const [reason, setReason] = useState("");
  const [hours, setHours] = useState<string>("");
  const [forfeitAck, setForfeitAck] = useState(false);
  const [specialRuleId, setSpecialRuleId] = useState<string>("");
  const [hrDirect, setHrDirect] = useState(false);
  const [hrReasonEdited, setHrReasonEdited] = useState<string | null>(null);

  const setTypeId = (id: string) => {
    setTypeIdState(id);
    if (rememberType && id) writeLastTypeId(id);
  };

  const { data: route } = useQuery({ queryKey: ["approvalRoute"], queryFn: leaveApi.approvalRoute });

  const usableTypes = useMemo(() => types.filter((t) => t.policyEnabled), [types]);
  const selectedType: LeaveType | undefined = useMemo(
    () => usableTypes.find((t) => String(t.id) === typeId),
    [usableTypes, typeId],
  );
  // 종일이 아닌 종류(반차·시간차)는 하루만 신청할 수 있다
  const isPartial = !!selectedType && selectedType.portion !== "FULL";
  const isHourly = selectedType?.portion === "HOURLY";
  const endDate = isPartial ? start : end;
  // 경조사 규정이 연결된 종류는 규정 하나를 반드시 선택해야 한다
  const specialRules = selectedType?.specialRules ?? [];
  const needsRule = specialRules.length > 0;
  // 팀장이 오늘 부재면 인사관리자에게 바로 신청할 수 있다(사유 필수, 기본 문구는 수정 가능)
  const hrDirectOn = hrDirect && !!route?.hrDirectAvailable;
  const hrReasonDefault = selectedType
    ? `팀장 ${route?.leadName ?? ""}님의 ${route?.leadAbsenceType ?? ""} 부재로 인사관리자에게 이 ${selectedType.name}${objectParticle(selectedType.name)} 신청합니다.`
    : "";
  const hrReason = hrReasonEdited ?? hrReasonDefault;

  // 기억해 둔 종류가 더 이상 쓸 수 없는 종류면(정책 변경 등) 선택하지 않은 상태로 둔다
  useEffect(() => {
    if (types.length > 0 && typeId && !selectedType) setTypeIdState("");
  }, [types.length, typeId, selectedType]);

  // 병가·공가처럼 잔여 연차 소진 후 쓰는 종류는 목록을 열기 전에 신청 가능 여부를 미리 조회해,
  // 불가하면 드롭다운에서 회색으로 표시하고 선택할 수 없게 한다.
  const restrictedTypes = useMemo(() => usableTypes.filter((t) => t.requiresAnnualExhausted), [usableTypes]);
  const restrictedEligibility = useQueries({
    queries: restrictedTypes.map((t) => ({
      queryKey: ["eligibility", String(t.id), start],
      queryFn: () => leaveApi.eligibility(t.id, start || undefined),
    })),
  });
  const blockedReasons = new Map<number, string>();
  const blockedNames: string[] = [];
  let blockedRemaining: number | null = null;
  restrictedTypes.forEach((t, i) => {
    const e = restrictedEligibility[i]?.data;
    if (e && !e.allowed) {
      blockedReasons.set(t.id, e.reason ?? "지금은 신청할 수 없습니다.");
      blockedNames.push(t.name);
      blockedRemaining = e.remainingDays;
    }
  });
  // 불가 사유는 종류마다 같은 조건이라 한 줄로 합쳐 보여 준다(예: "병가·공가는 …")
  const blockedHint = (() => {
    if (blockedNames.length === 0) return null;
    const names = blockedNames.join("·");
    if (blockedRemaining !== null && blockedRemaining >= 1) {
      return `${names}${topicParticle(names)} 잔여 연차를 1일 미만으로 모두 사용한 뒤 신청할 수 있습니다. (현재 잔여 ${formatDays(blockedRemaining)}일)`;
    }
    return `결재 대기 중인 연차·반차 신청이 있어 ${names}${objectParticle(names)} 신청할 수 없습니다. 대기 중인 신청이 처리된 뒤 신청해 주세요.`;
  })();

  // 신청 미리보기: 신청과 같은 계산으로 근무일·차감·신청 후 잔여와 불가 사유를 받는다
  const previewEnabled = !!selectedType && !!start && !!endDate && (!isHourly || !!hours);
  const { data: preview, isFetching: previewLoading } = useQuery({
    queryKey: ["leavePreview", typeId, start, endDate, hours, specialRuleId],
    queryFn: () =>
      leaveApi.preview({
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate,
        hours: isHourly ? Number(hours) : undefined,
        specialRuleId: specialRuleId ? Number(specialRuleId) : undefined,
      }),
    enabled: previewEnabled,
    placeholderData: (prev) => prev,
  });
  const eligibility = previewEnabled ? preview : undefined;
  const forfeitDays = eligibility?.forfeitDays ?? 0;
  const notAllowed = !!eligibility && !eligibility.allowed;

  // 시작일을 바꿔(연도가 달라지는 등) 선택해 둔 종류가 불가해지면 선택을 해제한다
  const selectedBlocked = !!selectedType && blockedReasons.has(selectedType.id);
  useEffect(() => {
    if (selectedBlocked) setTypeIdState("");
  }, [selectedBlocked]);

  // 종류를 바꾸면 규정 선택을 초기화한다
  useEffect(() => {
    setSpecialRuleId("");
  }, [typeId]);

  // 종류·날짜가 바뀌면 소멸 안내 확인을 다시 받는다
  useEffect(() => {
    setForfeitAck(false);
  }, [typeId, start]);

  const save = useMutation({
    mutationFn: () => {
      const body: LeaveRequestCreate = {
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate,
        reason: reason || undefined,
        hours: isHourly ? Number(hours) : undefined,
        forfeitAcknowledged: forfeitDays > 0 ? true : undefined,
        specialRuleId: needsRule ? Number(specialRuleId) : undefined,
        hrDirectReason: hrDirectOn ? hrReason.trim() : undefined,
      };
      return leaveApi.create(body);
    },
    onSuccess: (response) => {
      toast({ title: "휴가를 신청했습니다.", description: response.requestWarning ?? undefined,
        variant: response.requestWarning ? "default" : "success" });
      qc.invalidateQueries({ queryKey: ["leavePreview"] });
      qc.invalidateQueries({ queryKey: ["eligibility"] });
      onSaved(response.requestWarning);
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const canSubmit =
    !!selectedType &&
    !save.isPending &&
    !previewLoading &&
    !!eligibility &&
    !notAllowed &&
    (!isHourly || !!hours) &&
    (!needsRule || !!specialRuleId) &&
    (!hrDirectOn || !!hrReason.trim()) &&
    (forfeitDays <= 0 || forfeitAck);

  return {
    usableTypes,
    typeId,
    setTypeId,
    selectedType,
    isPartial,
    isHourly,
    blockedReasons,
    blockedHint,
    specialRules,
    needsRule,
    specialRuleId,
    setSpecialRuleId,
    hours,
    setHours,
    reason,
    setReason,
    eligibility,
    notAllowed,
    forfeitDays,
    forfeitAck,
    setForfeitAck,
    route,
    hrDirect,
    setHrDirect,
    hrReason,
    setHrReasonEdited,
    canSubmit,
    isSaving: save.isPending,
    submit: () => save.mutate(),
  };
}

/**
 * 휴가 신청 입력 칸. 날짜 영역은 화면마다 달라(다이얼로그는 날짜 입력, 캘린더 패널은 선택 요약) dates 로 받는다.
 */
export function LeaveRequestFields({ form, dates }: { form: LeaveRequestFormState; dates: React.ReactNode }) {
  const {
    usableTypes,
    typeId,
    setTypeId,
    blockedReasons,
    blockedHint,
    needsRule,
    specialRules,
    specialRuleId,
    setSpecialRuleId,
    isHourly,
    hours,
    setHours,
    eligibility,
    notAllowed,
    forfeitDays,
    forfeitAck,
    setForfeitAck,
    reason,
    setReason,
    route,
    hrDirect,
    setHrDirect,
    hrReason,
    setHrReasonEdited,
  } = form;

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label>휴가 종류</Label>
        <Select value={typeId} onValueChange={setTypeId}>
          <SelectTrigger>
            <SelectValue placeholder="종류 선택" />
          </SelectTrigger>
          <SelectContent>
            {usableTypes.map((t) => (
              <SelectItem key={t.id} value={String(t.id)} disabled={blockedReasons.has(t.id)}>
                {t.name} ({formatDays(t.deductDays)}일 차감)
                {blockedReasons.has(t.id) && " · 선택 불가"}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {blockedHint && <p className="text-xs text-muted-foreground">{blockedHint}</p>}
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
          <p className="text-xs text-muted-foreground">
            {specialRuleId
              ? `근무일 기준(주말·공휴일 제외) 최대 ${formatDays(specialRules.find((r) => String(r.id) === specialRuleId)?.days)}일까지 신청할 수 있습니다.`
              : "근무일 기준(주말·공휴일 제외) 규정 일수까지 신청할 수 있습니다."}
          </p>
        </div>
      )}
      {dates}
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
      {notAllowed && eligibility?.reason && (
        <p className="text-sm text-destructive">{eligibility.reason}</p>
      )}
      {eligibility?.allowed && <DeductionSummary form={form} />}
      {forfeitDays > 0 && (
        <div className="space-y-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <p>승인되면 남은 연차 {formatDays(forfeitDays)}일이 소멸됩니다(취소 시 복구).</p>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={forfeitAck}
              onChange={(e) => setForfeitAck(e.target.checked)}
            />
            안내를 확인했습니다
          </label>
        </div>
      )}
      <div className="space-y-2">
        <Label>사유 (선택)</Label>
        <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="예: 개인 사유" />
      </div>
      {route && (
        <p className="text-sm text-muted-foreground">
          {route.firstStage === "LEAD"
            ? `팀장(${route.leadName ?? "-"}) 1차 승인 → 인사관리자 최종 승인`
            : "인사관리자가 결재합니다"}
        </p>
      )}
      {route?.hrDirectAvailable && (
        <div className="space-y-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <p>
            팀장({route.leadName ?? "-"})님이 오늘 {route.leadAbsenceType ?? "휴가"}
            {roParticle(route.leadAbsenceType ?? "휴가")} 부재 중입니다.
          </p>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={hrDirect} onChange={(e) => setHrDirect(e.target.checked)} />
            인사관리자에게 바로 신청
          </label>
          {hrDirect && (
            <div className="space-y-1">
              <Label className="text-xs">사유(필수)</Label>
              <Input
                value={hrReason}
                maxLength={500}
                onChange={(e) => setHrReasonEdited(e.target.value)}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** 차감 요약: "연차 차감 2일 · 잔여 15일 (대기 1일) → 신청 후 12일" */
function DeductionSummary({ form }: { form: LeaveRequestFormState }) {
  const { eligibility, selectedType, isPartial } = form;
  if (!eligibility || !selectedType) return null;
  const deduction = eligibility.deduction ?? 0;
  const pending = eligibility.pendingDays ?? 0;
  const after = eligibility.remainingAfter ?? 0;
  return (
    <div className="space-y-1 rounded-md border bg-muted/40 p-3 text-sm">
      <p>
        {!isPartial && eligibility.workdays != null && <>근무일 {eligibility.workdays}일 · </>}
        {deduction > 0 ? `연차 ${formatDays(deduction)}일 차감` : "연차 차감 없음"}
      </p>
      <p className="text-muted-foreground">
        잔여 {formatDays(eligibility.remainingDays)}일
        {pending > 0 && ` (대기 ${formatDays(pending)}일)`} → 신청 후{" "}
        <span className={cn("font-semibold", after < 0 ? "text-destructive" : "text-foreground")}>
          {formatDays(after)}일
        </span>
      </p>
    </div>
  );
}
