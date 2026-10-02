import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { CalendarPlus, Check, X, Inbox } from "lucide-react";
import { leaveApi } from "@/api/leave";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
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
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { useAuthStore } from "@/store/auth";
import type { LeaveRequest } from "@/types";
import { formatDays, formatLeaveAmount, formatSpecialRule } from "@/lib/leaveFormat";
import LeaveListTab from "./LeaveListTab";
import LeaveRegisterDialog from "./LeaveRegisterDialog";

type RejectTarget = { req: LeaveRequest; mode: "reject" | "cancelReject" };

/** 확인창용 요약: "홍길동 · 연차 2026-10-14 ~ 2026-10-15 (2일)" */
function summary(r: LeaveRequest): string {
  const period = r.startDate === r.endDate ? r.startDate : `${r.startDate} ~ ${r.endDate}`;
  const rule = r.specialRuleName ? ` ${formatSpecialRule(r)}` : "";
  return `${r.employeeName} · ${r.leaveTypeName}${rule} ${period} (${formatLeaveAmount(r)})`;
}

/** 다른 결재자가 먼저 처리한 신청(409): 목록을 새로 불러와야 한다. */
function isAlreadyProcessed(e: unknown): boolean {
  return axios.isAxiosError(e) && e.response?.status === 409;
}

export default function ApprovalsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const canRegister = useAuthStore((s) => s.hasAnyRole("HR_ADMIN", "SYSTEM_ADMIN"));
  const [registering, setRegistering] = useState(false);

  const onApprove = async (r: LeaveRequest) => {
    const base = r.approvalWarning ? `${summary(r)}\n⚠ ${r.approvalWarning}` : summary(r);
    const ok = await confirm({
      title: r.ownRequest ? "본인 신청을 자가 승인할까요?" : "휴가를 승인할까요?",
      description: r.ownRequest ? `${base}\n자가 승인 기록이 이벤트 로그에 남습니다.` : base,
      confirmText: "승인",
    });
    if (ok) approve.mutate(r.id);
  };

  const onApproveCancel = async (r: LeaveRequest) => {
    const ok = await confirm({
      title: "휴가 취소 요청을 승인할까요?",
      description: `${summary(r)}\n승인하면 휴가가 취소되고 차감된 연차가 돌아갑니다.`,
      confirmText: "취소 승인",
    });
    if (ok) approveCancel.mutate(r.id);
  };
  const [rejecting, setRejecting] = useState<RejectTarget | null>(null);

  const { data: pending = [], isLoading } = useQuery({
    queryKey: ["pendingApprovals"],
    queryFn: leaveApi.pending,
  });
  const { sorted, sort, toggle } = useTableSort(pending, {
    // 구분: 신규 → 취소요청, 같은 구분 안에서는 본인 신청 먼저
    kind: (r) => (r.status === "CANCEL_REQUESTED" ? 2 : 0) + (r.ownRequest ? 0 : 1),
    employee: (r) => r.employeeName,
    department: (r) => r.departmentName,
    type: (r) => r.leaveTypeName,
    period: (r) => `${r.startDate} ${r.endDate}`,
    days: (r) => r.days,
    reason: (r) => (r.status === "CANCEL_REQUESTED" ? r.cancelReason : r.reason),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["pendingApprovals"] });
    qc.invalidateQueries({ queryKey: ["calendarEvents"] });
    qc.invalidateQueries({ queryKey: ["leaveList"] });
  };
  const onError = (e: unknown) => {
    toast({ title: extractErrorMessage(e), variant: "destructive" });
    if (isAlreadyProcessed(e)) invalidate();
  };

  const approve = useMutation({
    mutationFn: (id: number) => leaveApi.approve(id),
    onSuccess: () => {
      toast({ title: "승인되었습니다.", variant: "success" });
      invalidate();
    },
    onError,
  });

  const approveCancel = useMutation({
    mutationFn: (id: number) => leaveApi.approveCancel(id),
    onSuccess: () => {
      toast({ title: "취소 요청을 승인했습니다.", variant: "success" });
      invalidate();
    },
    onError,
  });

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
        <TabsContent value="pending">
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortableTableHead sortKey="kind" sort={sort} onSort={toggle}>구분</SortableTableHead>
                    <SortableTableHead sortKey="employee" sort={sort} onSort={toggle}>신청자</SortableTableHead>
                    <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
                    <SortableTableHead sortKey="type" sort={sort} onSort={toggle}>종류</SortableTableHead>
                    <SortableTableHead sortKey="period" sort={sort} onSort={toggle}>기간</SortableTableHead>
                    <SortableTableHead sortKey="days" sort={sort} onSort={toggle}>일수</SortableTableHead>
                    <SortableTableHead sortKey="reason" sort={sort} onSort={toggle}>사유</SortableTableHead>
                    <TableHead className="text-right">결재</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {isLoading ? (
                    <TableRow>
                      <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                        불러오는 중…
                      </TableCell>
                    </TableRow>
                  ) : pending.length > 0 ? (
                    sorted.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell>
                          <div className="flex flex-wrap gap-1">
                            {r.status === "CANCEL_REQUESTED" ? (
                              <Badge variant="destructive">취소요청</Badge>
                            ) : (
                              <Badge variant="secondary">신규</Badge>
                            )}
                            {r.ownRequest && (
                              <Badge variant="warning" title="결재자 본인의 신청입니다(자가 승인)">
                                본인 신청
                              </Badge>
                            )}
                          </div>
                        </TableCell>
                        <TableCell className="font-medium">{r.employeeName}</TableCell>
                        <TableCell>{r.departmentName ?? "-"}</TableCell>
                        <TableCell>
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="inline-block h-3 w-3 rounded-full"
                              style={{ backgroundColor: r.leaveTypeColor }}
                            />
                            {r.leaveTypeName}
                            {r.specialRuleName && (
                              <span className="text-xs text-muted-foreground">{formatSpecialRule(r)}</span>
                            )}
                          </span>
                          {r.approvalWarning && (
                            <p className="mt-1 text-xs text-amber-700">⚠ {r.approvalWarning}</p>
                          )}
                        </TableCell>
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
                        </TableCell>
                        <TableCell className="max-w-[180px] truncate text-muted-foreground">
                          {(r.status === "CANCEL_REQUESTED" ? r.cancelReason : r.reason) ?? "-"}
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            {r.status === "CANCEL_REQUESTED" ? (
                              <>
                                <Button
                                  size="sm"
                                  onClick={() => onApproveCancel(r)}
                                  disabled={approveCancel.isPending}
                                >
                                  <Check className="h-4 w-4" /> 취소 승인
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() => setRejecting({ req: r, mode: "cancelReject" })}
                                >
                                  <X className="h-4 w-4" /> 취소 반려
                                </Button>
                              </>
                            ) : (
                              <>
                                <Button
                                  size="sm"
                                  onClick={() => onApprove(r)}
                                  disabled={approve.isPending}
                                >
                                  <Check className="h-4 w-4" /> 승인
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() => setRejecting({ req: r, mode: "reject" })}
                                >
                                  <X className="h-4 w-4" /> 반려
                                </Button>
                              </>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={8} className="py-10 text-center text-muted-foreground">
                        <Inbox className="mx-auto mb-2 h-8 w-8 opacity-40" />
                        대기 중인 결재가 없습니다.
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

      {rejecting && (
        <RejectDialog
          target={rejecting}
          onClose={() => setRejecting(null)}
          onDone={() => {
            setRejecting(null);
            invalidate();
          }}
        />
      )}

      {registering && <LeaveRegisterDialog onClose={() => setRegistering(false)} />}
    </div>
  );
}

function RejectDialog({
  target,
  onClose,
  onDone,
}: {
  target: RejectTarget;
  onClose: () => void;
  onDone: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const [reason, setReason] = useState("");
  const isCancel = target.mode === "cancelReject";
  const onReject = async () => {
    const ok = await confirm({
      title: isCancel ? "휴가 취소 요청을 반려할까요?" : "휴가 신청을 반려할까요?",
      description: `${summary(target.req)}\n사유: ${reason.trim() || "(미기재)"}`,
      confirmText: "반려",
      destructive: true,
    });
    if (ok) reject.mutate();
  };
  const reject = useMutation({
    mutationFn: () =>
      isCancel
        ? leaveApi.rejectCancel(target.req.id, reason)
        : leaveApi.reject(target.req.id, reason),
    onSuccess: () => {
      toast({ title: "반려되었습니다.", variant: "success" });
      onDone();
    },
    onError: (e) => {
      toast({ title: extractErrorMessage(e), variant: "destructive" });
      // 다른 결재자가 먼저 처리했으면 창을 닫고 목록을 새로 불러온다
      if (isAlreadyProcessed(e)) onDone();
    },
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>
            {target.req.employeeName} 님의 {isCancel ? "취소 요청 반려" : "신청 반려"}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          <Label>반려 사유</Label>
          <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="사유를 입력하세요" />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button variant="destructive" onClick={onReject} disabled={reject.isPending}>
            반려
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
