import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { extractErrorMessage } from "@/api/client";

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
                    <TableCell>{r.days}</TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[r.status]}>{LEAVE_STATUS_LABEL[r.status]}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{r.approverName ?? "-"}</TableCell>
                    <TableCell className="text-right">
                      {r.status === "PENDING" && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            if (confirm("신청을 취소할까요?")) cancel.mutate({ id: r.id, approved: false });
                          }}
                        >
                          <X className="h-4 w-4" /> 취소
                        </Button>
                      )}
                      {r.status === "APPROVED" && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            if (confirm("승인된 휴가의 취소를 요청할까요?\n팀장 승인 후 확정됩니다."))
                              cancel.mutate({ id: r.id, approved: true });
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
        <p className={`mt-1 text-2xl font-bold ${accent ? "text-primary" : ""}`}>{value}<span className="ml-1 text-sm font-normal text-muted-foreground">일</span></p>
      </CardContent>
    </Card>
  );
}

function RequestDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { toast } = useToast();
  const { data: types = [] } = useQuery({ queryKey: ["leaveTypes", "active"], queryFn: leaveApi.activeTypes });
  const [typeId, setTypeId] = useState<string>("");
  const today = new Date().toISOString().slice(0, 10);
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const [reason, setReason] = useState("");

  const selectedType: LeaveType | undefined = useMemo(
    () => types.find((t) => String(t.id) === typeId),
    [types, typeId],
  );
  const isHalf = selectedType?.halfDay ?? false;

  const save = useMutation({
    mutationFn: () => {
      const body: LeaveRequestCreate = {
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate: isHalf ? start : end,
        reason: reason || undefined,
      };
      return leaveApi.create(body);
    },
    onSuccess: () => {
      toast({ title: "휴가를 신청했습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

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
                {types.map((t) => (
                  <SelectItem key={t.id} value={String(t.id)}>
                    {t.name} ({t.deductDays}일 차감)
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{isHalf ? "날짜" : "시작일"}</Label>
              <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
            </div>
            {!isHalf && (
              <div className="space-y-2">
                <Label>종료일</Label>
                <Input type="date" value={end} min={start} onChange={(e) => setEnd(e.target.value)} />
              </div>
            )}
          </div>
          <div className="space-y-2">
            <Label>사유 (선택)</Label>
            <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="예: 개인 사유" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={() => save.mutate()} disabled={!typeId || save.isPending}>
            신청
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
