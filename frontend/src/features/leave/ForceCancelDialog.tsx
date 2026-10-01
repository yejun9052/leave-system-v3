import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { extractErrorMessage } from "@/api/client";
import { leaveApi } from "@/api/leave";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

/** 강제 취소할 휴가(캘린더 날짜 상세·결재함 휴가 목록에서 넘긴다) */
export interface ForceCancelTarget {
  id: number;
  employeeName: string;
  leaveTypeName: string;
  startDate: string;
  endDate: string;
}

/** 인사관리자·시스템 관리자의 강제 취소: 승인된 휴가를 시작 전후 상관없이 취소한다(사유 필수). */
export default function ForceCancelDialog({ leave, onClose }: { leave: ForceCancelTarget; onClose: () => void }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const [reason, setReason] = useState("");
  const period = leave.startDate === leave.endDate ? leave.startDate : `${leave.startDate} ~ ${leave.endDate}`;

  const cancel = useMutation({
    mutationFn: () => leaveApi.cancel(leave.id, reason.trim()),
    onSuccess: () => {
      toast({ title: "휴가를 강제 취소했습니다.", variant: "success" });
      qc.invalidateQueries({ queryKey: ["calendarDay"] });
      qc.invalidateQueries({ queryKey: ["calendarEvents"] });
      qc.invalidateQueries({ queryKey: ["pendingApprovals"] });
      qc.invalidateQueries({ queryKey: ["leaveList"] });
      onClose();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onSubmit = async () => {
    const ok = await confirm({
      title: "승인된 휴가를 강제 취소할까요?",
      description:
        `${leave.employeeName} · ${leave.leaveTypeName} ${period}\n사유: ${reason.trim()}` +
        "\n차감된 연차가 돌아가고, 본인과 담당 팀장에게 알림이 갑니다.",
      confirmText: "강제 취소",
      cancelText: "닫기",
      destructive: true,
    });
    if (ok) cancel.mutate();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{leave.employeeName} 님의 휴가 강제 취소</DialogTitle>
          <DialogDescription>
            {leave.leaveTypeName} {period}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label>취소 사유 (필수)</Label>
          <Input
            value={reason}
            maxLength={500}
            onChange={(e) => setReason(e.target.value)}
            placeholder="예: 업무 일정 변경으로 본인 요청"
            autoFocus
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          <Button variant="destructive" onClick={onSubmit} disabled={!reason.trim() || cancel.isPending}>
            강제 취소
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
