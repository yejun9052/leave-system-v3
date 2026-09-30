import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, CalendarDays, X } from "lucide-react";
import { leaveApi, type LeaveRequestCreate } from "@/api/leave";
import { LEAVE_STATUS_LABEL, type LeaveRequestStatus, type LeaveType } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { formatDays, formatLeaveAmount } from "@/lib/leaveFormat";

const STATUS_VARIANT: Record<LeaveRequestStatus, "default" | "success" | "warning" | "destructive" | "secondary"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  CANCEL_REQUESTED: "warning",
  CANCELLED: "secondary",
};

export default function MyLeavesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const [open, setOpen] = useState(false);

  const { data: balance } = useQuery({ queryKey: ["myBalance"], queryFn: () => leaveApi.myBalance() });
  const { data: requests } = useQuery({ queryKey: ["myRequests"], queryFn: () => leaveApi.myRequests() });

  const cancel = useMutation({
    mutationFn: ({ id, approved }: { id: number; approved: boolean }) =>
      leaveApi.cancel(id).then((r) => ({ r, approved })),
    onSuccess: ({ approved }) => {
      toast({
        title: approved ? "취소 요청되었습니다. 팀장 승인 후 확정됩니다." : "신청이 취소되었습니다.",
        variant: "success",
      });
      qc.invalidateQueries({ queryKey: ["myRequests"] });
      qc.invalidateQueries({ queryKey: ["myBalance"] });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">내 휴가</h1>
          <p className="text-sm text-muted-foreground">잔여 연차 확인 및 휴가 신청</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus className="h-4 w-4" /> 휴가 신청
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="잔여 연차" value={balance?.remaining ?? 0} accent />
        <StatCard label="부여" value={balance?.granted ?? 0} />
        <StatCard label="사용" value={balance?.used ?? 0} />
        <StatCard label="대기중" value={balance?.pending ?? 0} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">신청 내역</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>종류</TableHead>
                <TableHead>기간</TableHead>
                <TableHead>일수</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>결재자</TableHead>
                <TableHead className="text-right">관리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {requests && requests.content.length > 0 ? (
                requests.content.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>
                      <span className="inline-flex items-center gap-2">
                        <span
                          className="inline-block h-3 w-3 rounded-full"
                          style={{ backgroundColor: r.leaveTypeColor }}
                        />
                        {r.leaveTypeName}
                      </span>
                    </TableCell>
                    <TableCell>
                      {r.startDate}
                      {r.startDate !== r.endDate && ` ~ ${r.endDate}`}
                    </TableCell>
                    <TableCell>
                      {formatLeaveAmount(r)}
                      {r.forfeitedDays > 0 && (
                        <span className="ml-2 text-xs text-muted-foreground">
                          연차 {formatDays(r.forfeitedDays)}일 소멸
                        </span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[r.status]}>{LEAVE_STATUS_LABEL[r.status]}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{r.approverName ?? "-"}</TableCell>
                    <TableCell className="text-right">
                      {r.status === "PENDING" && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={async () => {
                            const ok = await confirm({
                              title: "휴가 신청을 취소할까요?",
                              description: `${r.leaveTypeName} ${r.startDate}${r.startDate !== r.endDate ? ` ~ ${r.endDate}` : ""} (${formatLeaveAmount(r)})`,
                              confirmText: "신청 취소",
                              cancelText: "닫기",
                              destructive: true,
                            });
                            if (ok) cancel.mutate({ id: r.id, approved: false });
                          }}
                        >
                          <X className="h-4 w-4" /> 취소
                        </Button>
                      )}
                      {r.status === "APPROVED" && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={async () => {
                            const ok = await confirm({
                              title: "승인된 휴가의 취소를 요청할까요?",
                              description: `${r.leaveTypeName} ${r.startDate}${r.startDate !== r.endDate ? ` ~ ${r.endDate}` : ""} (${formatLeaveAmount(r)})\n결재자가 승인하면 취소가 확정됩니다.`,
                              confirmText: "취소 요청",
                              cancelText: "닫기",
                            });
                            if (ok) cancel.mutate({ id: r.id, approved: true });
                          }}
                        >
                          <X className="h-4 w-4" /> 취소 요청
                        </Button>
                      )}
                      {r.status === "CANCEL_REQUESTED" && (
                        <span className="text-xs text-muted-foreground">취소 승인 대기</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    <CalendarDays className="mx-auto mb-2 h-8 w-8 opacity-40" />
                    신청 내역이 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {open && <RequestDialog onClose={() => setOpen(false)} onSaved={() => {
        setOpen(false);
        qc.invalidateQueries({ queryKey: ["myRequests"] });
        qc.invalidateQueries({ queryKey: ["myBalance"] });
      }} />}
    </div>
  );
}

function StatCard({ label, value, accent }: { label: string; value: number; accent?: boolean }) {
  return (
    <Card className={accent ? "border-primary/40 bg-primary/5" : undefined}>
      <CardContent className="p-4">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className={`mt-1 text-2xl font-bold ${accent ? "text-primary" : ""}`}>{formatDays(value)}<span className="ml-1 text-sm font-normal text-muted-foreground">일</span></p>
      </CardContent>
    </Card>
  );
}

/** 마지막 글자에 받침이 있는지(한글이 아니면 받침 없음으로 본다). */
function hasFinalConsonant(word: string): boolean {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}
const topicParticle = (word: string) => (hasFinalConsonant(word) ? "은" : "는");
const objectParticle = (word: string) => (hasFinalConsonant(word) ? "을" : "를");

function RequestDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { toast } = useToast();
  const { data: types = [] } = useQuery({ queryKey: ["leaveTypes", "active"], queryFn: leaveApi.activeTypes });
  const [typeId, setTypeId] = useState<string>("");
  const today = new Date().toISOString().slice(0, 10);
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const [reason, setReason] = useState("");
  const [hours, setHours] = useState<string>("");
  const [forfeitAck, setForfeitAck] = useState(false);

  const usableTypes = useMemo(() => types.filter((t) => t.policyEnabled), [types]);
  const selectedType: LeaveType | undefined = useMemo(
    () => usableTypes.find((t) => String(t.id) === typeId),
    [usableTypes, typeId],
  );
  // 종일이 아닌 종류(반차·반반차·시간차)는 하루만 신청할 수 있다
  const isPartial = !!selectedType && selectedType.portion !== "FULL";
  const isHourly = selectedType?.portion === "HOURLY";

  // 병가·공가처럼 잔여 연차 소진 후 쓰는 종류는 목록을 열기 전에 신청 가능 여부를 미리 조회해,
  // 불가하면 드롭다운에서 회색으로 표시하고 선택할 수 없게 한다(아래 단건 조회와 같은 캐시 키).
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

  const { data: eligibility, isFetching: eligibilityLoading } = useQuery({
    queryKey: ["eligibility", typeId, start],
    queryFn: () => leaveApi.eligibility(Number(typeId), start || undefined),
    enabled: !!selectedType,
  });
  const forfeitDays = eligibility?.forfeitDays ?? 0;
  const notAllowed = !!eligibility && !eligibility.allowed;

  // 시작일을 바꿔(연도가 달라지는 등) 선택해 둔 종류가 불가해지면 선택을 해제한다
  const selectedBlocked = !!selectedType && blockedReasons.has(selectedType.id);
  useEffect(() => {
    if (selectedBlocked) setTypeId("");
  }, [selectedBlocked]);

  // 종류·날짜가 바뀌면 소멸 안내 확인을 다시 받는다
  useEffect(() => {
    setForfeitAck(false);
  }, [typeId, start]);

  const save = useMutation({
    mutationFn: () => {
      const body: LeaveRequestCreate = {
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate: isPartial ? start : end,
        reason: reason || undefined,
        hours: isHourly ? Number(hours) : undefined,
        forfeitAcknowledged: forfeitDays > 0 ? true : undefined,
      };
      return leaveApi.create(body);
    },
    onSuccess: () => {
      toast({ title: "휴가를 신청했습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const canSubmit =
    !!selectedType &&
    !save.isPending &&
    !eligibilityLoading &&
    !notAllowed &&
    (!isHourly || !!hours) &&
    (forfeitDays <= 0 || forfeitAck);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>휴가 신청</DialogTitle>
        </DialogHeader>
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
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{isPartial ? "날짜" : "시작일"}</Label>
              <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>종료일</Label>
              <Input
                type="date"
                value={isPartial ? start : end}
                min={start}
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
          {notAllowed && eligibility?.reason && (
            <p className="text-sm text-destructive">{eligibility.reason}</p>
          )}
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
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={() => save.mutate()} disabled={!canSubmit}>
            신청
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
