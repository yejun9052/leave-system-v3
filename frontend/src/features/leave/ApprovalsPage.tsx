import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { invalidateLeaveData } from "@/lib/invalidate";
import QueryError from "@/components/QueryError";
import axios from "axios";
import { AlertTriangle, CalendarPlus, Check, X, Inbox } from "lucide-react";
import { leaveApi } from "@/api/leave";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  SortableTableHead,
} from "@/components/ui/table";
import { useTableSort } from "@/lib/useTableSort";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { useAuthStore } from "@/store/auth";
import type { LeaveRequest } from "@/types";
import { formatDays, formatLeaveAmount, formatPeriodSplit, formatSpecialRule } from "@/lib/leaveFormat";
import { formatDateTime } from "@/lib/dateFormat";
import LeaveListTab from "./LeaveListTab";
import LeaveRegisterDialog from "./LeaveRegisterDialog";

/** 결재 창: review = 신청 내용을 보고 승인(또는 반려로 넘어감), reject = 반려 사유 입력 */
type DecisionTarget = { req: LeaveRequest; step: "review" | "reject" };

function isCancelRequest(r: LeaveRequest): boolean {
  return r.status === "CANCEL_REQUESTED";
}

/** 확인창용 요약: "홍길동 · 연차 2026-10-14 ~ 2026-10-15 (2일)" */
function summary(r: LeaveRequest): string {
  const period = r.startDate === r.endDate ? r.startDate : `${r.startDate} ~ ${r.endDate}`;
  const rule = r.specialRuleName ? ` ${formatSpecialRule(r)}` : "";
  return `${r.employeeName} · ${r.leaveTypeName}${rule} ${period} (${formatLeaveAmount(r)})`;
}

/** 구분: "연차 신청", "공가 신청", "연차 취소 요청" */
function kindLabel(r: LeaveRequest): string {
  return `${r.leaveTypeName} ${r.status === "CANCEL_REQUESTED" ? "취소 요청" : "신청"}`;
}

type KindFilter = "ALL" | "PENDING" | "CANCEL_REQUESTED";
const KIND_FILTERS: { value: KindFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "PENDING", label: "승인 요청" },
  { value: "CANCEL_REQUESTED", label: "취소 요청" },
];

const RECENT_MS = 24 * 60 * 60 * 1000;

/** 결재가 들어온 지 24시간이 안 된 요청 */
function isRecent(r: LeaveRequest): boolean {
  return Date.now() - new Date(r.requestedAt).getTime() < RECENT_MS;
}

/** 다른 결재자가 먼저 처리한 신청(409): 목록을 새로 불러와야 한다. */
function isAlreadyProcessed(e: unknown): boolean {
  return axios.isAxiosError(e) && e.response?.status === 409;
}

export default function ApprovalsPage() {
  const qc = useQueryClient();
  const canRegister = useAuthStore((s) => s.hasAnyRole("HR_ADMIN", "SYSTEM_ADMIN"));
  const [registering, setRegistering] = useState(false);

  const [deciding, setDeciding] = useState<DecisionTarget | null>(null);

  const { data: pending = [], isLoading, isError, error, refetch } = useQuery({
    queryKey: ["pendingApprovals"],
    queryFn: leaveApi.pending,
  });
  const [kindFilter, setKindFilter] = useState<KindFilter>("ALL");
  const countOf = (k: KindFilter) => (k === "ALL" ? pending.length : pending.filter((r) => r.status === k).length);
  const shown = kindFilter === "ALL" ? pending : pending.filter((r) => r.status === kindFilter);
  // 서버가 결재가 들어온 때 최신순으로 준다(취소 요청은 취소를 요청한 때)
  const { sorted, sort, toggle } = useTableSort(shown, {
    // 구분: 종류 이름 순, 같은 종류에서는 신청 → 취소 요청
    kind: (r) => `${r.leaveTypeName} ${r.status === "CANCEL_REQUESTED" ? 1 : 0}`,
    employee: (r) => r.employeeName,
    department: (r) => r.departmentName,
    period: (r) => `${r.startDate} ${r.endDate}`,
    days: (r) => r.days,
    reason: (r) => (r.status === "CANCEL_REQUESTED" ? r.cancelReason : r.reason),
  });

  const invalidate = () => invalidateLeaveData(qc);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">결재함</h1>
          <p className="text-sm text-muted-foreground">
            휴가 신청을 승인하거나 반려합니다. 결재자 한 명이 승인하면 확정됩니다.
          </p>
        </div>
        {canRegister && (
          <Button onClick={() => setRegistering(true)}>
            <CalendarPlus className="h-4 w-4" /> 휴가 직접 등록
          </Button>
        )}
      </div>

      <Tabs defaultValue="pending">
        <TabsList>
          <TabsTrigger value="pending">결재 대기{pending.length > 0 && ` (${pending.length})`}</TabsTrigger>
          <TabsTrigger value="list">휴가 목록</TabsTrigger>
        </TabsList>
        <TabsContent value="pending" className="space-y-3">
          {/* 실패를 "대기 중인 결재가 없습니다"로 보이면 결재할 건이 없다고 오해한다 */}
          {isError && <QueryError error={error} onRetry={() => void refetch()} />}
          <div className="space-y-1">
            <Label htmlFor="approval-kind" className="text-xs text-muted-foreground">구분</Label>
            <Select value={kindFilter} onValueChange={(v) => setKindFilter(v as KindFilter)}>
              <SelectTrigger id="approval-kind" className="w-[180px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {KIND_FILTERS.map((f) => (
                  <SelectItem key={f.value} value={f.value}>
                    {f.label} ({countOf(f.value)})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortableTableHead sortKey="kind" sort={sort} onSort={toggle}>구분</SortableTableHead>
                    <SortableTableHead sortKey="employee" sort={sort} onSort={toggle}>신청자</SortableTableHead>
                    <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
                    <SortableTableHead sortKey="period" sort={sort} onSort={toggle}>기간</SortableTableHead>
                    <SortableTableHead sortKey="days" sort={sort} onSort={toggle}>일수</SortableTableHead>
                    <SortableTableHead sortKey="reason" sort={sort} onSort={toggle}>사유</SortableTableHead>
                    <TableHead className="text-right">결재</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {isLoading ? (
                    <TableRow>
                      <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                        불러오는 중…
                      </TableCell>
                    </TableRow>
                  ) : shown.length > 0 ? (
                    sorted.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell>
                          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                            {isRecent(r) && (
                              <Badge title="들어온 지 24시간이 안 된 요청입니다">최신</Badge>
                            )}
                            <span className="inline-flex items-center gap-2 whitespace-nowrap font-medium">
                              <span
                                className="inline-block h-3 w-3 shrink-0 rounded-full"
                                style={{ backgroundColor: r.leaveTypeColor }}
                              />
                              <span className={r.status === "CANCEL_REQUESTED" ? "text-destructive" : undefined}>
                                {kindLabel(r)}
                              </span>
                            </span>
                            {r.specialRuleName && (
                              <span className="text-xs text-muted-foreground">{formatSpecialRule(r)}</span>
                            )}
                            {r.ownRequest && (
                              <Badge variant="warning" title="결재자 본인의 신청입니다(자가 승인)">
                                본인 신청
                              </Badge>
                            )}
                          </div>
                          {r.approvalWarning && (
                            <p className="mt-1 text-xs text-amber-700">⚠ {r.approvalWarning}</p>
                          )}
                        </TableCell>
                        <TableCell className="font-medium">{r.employeeName}</TableCell>
                        <TableCell>{r.departmentName ?? "-"}</TableCell>
                        <TableCell>
                          {r.startDate}
                          {r.startDate !== r.endDate && ` ~ ${r.endDate}`}
                        </TableCell>
                        <TableCell>
                          <Badge variant="secondary">{formatLeaveAmount(r)}</Badge>
                          {r.forfeitedDays > 0 && (
                            <span className="ml-2 text-xs text-muted-foreground">
                              연차 {formatDays(r.forfeitedDays)}일 소멸
                            </span>
                          )}
                          {formatPeriodSplit(r) && (
                            <p className="mt-1 whitespace-nowrap text-xs text-muted-foreground">{formatPeriodSplit(r)}</p>
                          )}
                        </TableCell>
                        <TableCell className="max-w-[180px] truncate text-muted-foreground">
                          {(r.status === "CANCEL_REQUESTED" ? r.cancelReason : r.reason) ?? "-"}
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            <Button size="sm" onClick={() => setDeciding({ req: r, step: "review" })}>
                              <Check className="h-4 w-4" /> {isCancelRequest(r) ? "취소 승인" : "승인"}
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setDeciding({ req: r, step: "reject" })}
                            >
                              <X className="h-4 w-4" /> {isCancelRequest(r) ? "취소 반려" : "반려"}
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                        {isError ? (
                          "결재 목록을 불러오지 못했습니다."
                        ) : (
                          <>
                            <Inbox className="mx-auto mb-2 h-8 w-8 opacity-40" />
                            {kindFilter === "ALL"
                              ? "대기 중인 결재가 없습니다."
                              : `대기 중인 ${KIND_FILTERS.find((f) => f.value === kindFilter)?.label}이 없습니다.`}
                          </>
                        )}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="list">
          <LeaveListTab />
        </TabsContent>
      </Tabs>

      {deciding && (
        <DecisionDialog
          key={deciding.req.id}
          target={deciding}
          onStep={(step) => setDeciding({ ...deciding, step })}
          onClose={() => setDeciding(null)}
          onDone={() => {
            setDeciding(null);
            invalidate();
          }}
        />
      )}

      {registering && <LeaveRegisterDialog onClose={() => setRegistering(false)} />}
    </div>
  );
}

/**
 * 결재 창. 승인 버튼은 신청 내용(취소 요청은 원래 신청 사유·처음 승인·취소 사유까지)을 보여 주고 이 창에서 승인하거나
 * 반려로 넘어간다. 반려 버튼은 같은 내용 아래에 반려 사유를 받는다.
 */
function DecisionDialog({
  target,
  onStep,
  onClose,
  onDone,
}: {
  target: DecisionTarget;
  onStep: (step: DecisionTarget["step"]) => void;
  onClose: () => void;
  onDone: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const [reason, setReason] = useState("");
  const r = target.req;
  const isCancel = isCancelRequest(r);
  const rejecting = target.step === "reject";

  const onError = (e: unknown) => {
    toast({ title: extractErrorMessage(e), variant: "destructive" });
    // 다른 결재자가 먼저 처리했으면 창을 닫고 목록을 새로 불러온다
    if (isAlreadyProcessed(e)) onDone();
  };
  const approve = useMutation({
    mutationFn: () => (isCancel ? leaveApi.approveCancel(r.id) : leaveApi.approve(r.id)),
    onSuccess: () => {
      toast({ title: isCancel ? "취소 요청을 승인했습니다." : "승인되었습니다.", variant: "success" });
      onDone();
    },
    onError,
  });
  const reject = useMutation({
    mutationFn: () => (isCancel ? leaveApi.rejectCancel(r.id, reason) : leaveApi.reject(r.id, reason)),
    onSuccess: () => {
      toast({ title: "반려되었습니다.", variant: "success" });
      onDone();
    },
    onError,
  });
  const busy = approve.isPending || reject.isPending;

  const onReject = async () => {
    const ok = await confirm({
      title: isCancel ? "휴가 취소 요청을 반려할까요?" : "휴가 신청을 반려할까요?",
      description: `${summary(r)}\n사유: ${reason.trim() || "(미기재)"}`,
      confirmText: "반려",
      destructive: true,
    });
    if (ok) reject.mutate();
  };

  const title = rejecting
    ? `${r.employeeName} 님의 ${isCancel ? "취소 요청 반려" : "신청 반려"}`
    : isCancel
      ? "휴가 취소 요청 승인"
      : r.ownRequest
        ? "본인 신청 자가 승인"
        : "휴가 승인";

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {rejecting
              ? "반려 사유는 신청자에게 알림과 메일로 전달됩니다."
              : isCancel
                ? "승인하면 휴가가 취소되고 차감된 연차가 돌아갑니다."
                : r.ownRequest
                  ? "자가 승인 기록이 이벤트 로그에 남습니다."
                  : "신청 내용을 확인하고 승인하거나 반려합니다."}
          </DialogDescription>
        </DialogHeader>
        <RequestDetails r={r} />
        {rejecting && (
          <div className="space-y-2">
            <Label htmlFor="reject-reason">반려 사유</Label>
            <Input
              id="reject-reason"
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
              placeholder="사유를 입력하세요"
              autoFocus
            />
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          {rejecting ? (
            <Button variant="destructive" onClick={onReject} disabled={busy}>
              반려
            </Button>
          ) : (
            <>
              <Button variant="outline" onClick={() => onStep("reject")} disabled={busy}>
                <X className="h-4 w-4" /> {isCancel ? "취소 반려" : "반려"}
              </Button>
              <Button onClick={() => approve.mutate()} disabled={busy}>
                <Check className="h-4 w-4" /> {isCancel ? "취소 승인" : "승인"}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** 결재 창의 신청 내용. 취소 요청은 원래 신청 사유와 처음 승인, 취소 사유를 함께 보여 준다. */
function RequestDetails({ r }: { r: LeaveRequest }) {
  const isCancel = isCancelRequest(r);
  return (
    <div className="space-y-2">
      <dl className="grid grid-cols-[6.5rem_1fr] gap-x-3 gap-y-1.5 rounded-md bg-muted px-3 py-2 text-sm">
        <dt className="text-muted-foreground">신청자</dt>
        <dd className="flex flex-wrap items-center gap-x-2">
          {r.employeeName}
          {r.departmentName && <span className="text-muted-foreground">({r.departmentName})</span>}
          {r.ownRequest && <Badge variant="warning">본인 신청</Badge>}
        </dd>
        <dt className="text-muted-foreground">휴가 구분</dt>
        <dd className="flex flex-wrap items-center gap-x-2">
          <span className="inline-flex items-center gap-2">
            <span className="inline-block h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: r.leaveTypeColor }} />
            {r.leaveTypeName}
          </span>
          {r.specialRuleName && <span className="text-xs text-muted-foreground">{formatSpecialRule(r)}</span>}
        </dd>
        <dt className="text-muted-foreground">기간</dt>
        <dd>{r.startDate === r.endDate ? r.startDate : `${r.startDate} ~ ${r.endDate}`}</dd>
        <dt className="text-muted-foreground">일수</dt>
        <dd>
          {formatLeaveAmount(r)}
          {r.forfeitedDays > 0 && (
            <span className="ml-2 text-xs text-muted-foreground">연차 {formatDays(r.forfeitedDays)}일 소멸</span>
          )}
          {formatPeriodSplit(r) && <p className="text-xs text-muted-foreground">{formatPeriodSplit(r)}</p>}
        </dd>
        <dt className="text-muted-foreground">신청 사유</dt>
        <dd className="whitespace-pre-wrap break-words">{r.reason?.trim() || "미기재"}</dd>
        <dt className="text-muted-foreground">신청일</dt>
        <dd>{formatDateTime(r.createdAt)}</dd>
        {isCancel && (
          <>
            {r.approverName && (
              <>
                <dt className="text-muted-foreground">승인</dt>
                <dd>
                  {r.approverName}
                  {r.approvedAt && <span className="text-muted-foreground"> · {formatDateTime(r.approvedAt)}</span>}
                </dd>
              </>
            )}
            <dt className="font-medium text-destructive">취소 사유</dt>
            <dd className="whitespace-pre-wrap break-words">{r.cancelReason?.trim() || "미기재"}</dd>
            <dt className="text-muted-foreground">취소 요청일</dt>
            <dd>{formatDateTime(r.requestedAt)}</dd>
          </>
        )}
      </dl>
      {/* 결재 경고(팀 동시 부재 등)는 확인창과 같은 노란 상자로 눈에 띄게 */}
      {r.approvalWarning && (
        <p
          role="alert"
          className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
          <span className="whitespace-pre-line">{r.approvalWarning}</span>
        </p>
      )}
    </div>
  );
}
