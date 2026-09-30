import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, CalendarDays, X } from "lucide-react";
import { leaveApi } from "@/api/leave";
import { LEAVE_STATUS_LABEL, type LeaveRequestStatus } from "@/types";
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
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { formatDays, formatLeaveAmount, formatSpecialRule } from "@/lib/leaveFormat";
import { LeaveRequestFields, useLeaveRequestForm } from "./LeaveRequestForm";

const STATUS_VARIANT: Record<LeaveRequestStatus, "default" | "success" | "warning" | "destructive" | "secondary"> = {
  PENDING: "warning",
  LEAD_APPROVED: "default",
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
                        {r.specialRuleName && (
                          <span className="text-xs text-muted-foreground">{formatSpecialRule(r)}</span>
                        )}
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
                      {r.status === "LEAD_APPROVED" && (
                        <p className="mt-1 text-xs text-muted-foreground">
                          {r.leadApproverName ? `팀장 ${r.leadApproverName} 승인 · 인사 결재 대기` : "인사 결재 대기"}
                        </p>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{r.approverName ?? "-"}</TableCell>
                    <TableCell className="text-right">
                      {(r.status === "PENDING" || r.status === "LEAD_APPROVED") && (
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

function RequestDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const today = new Date().toISOString().slice(0, 10);
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const form = useLeaveRequestForm({ start, end, onSaved });
  const { isPartial } = form;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>휴가 신청</DialogTitle>
        </DialogHeader>
        <LeaveRequestFields
          form={form}
          dates={
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
          }
        />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={form.submit} disabled={!form.canSubmit}>
            신청
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
