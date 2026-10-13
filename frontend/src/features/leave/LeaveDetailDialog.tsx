import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDateTime } from "@/lib/dateFormat";
import { formatDays, formatLeaveAmount, formatPeriodSplit, formatSpecialRule } from "@/lib/leaveFormat";
import { LEAVE_STATUS_LABEL, type LeaveRequest, type LeaveRequestStatus } from "@/types";

const STATUS_VARIANT: Record<LeaveRequestStatus, "default" | "success" | "warning" | "destructive" | "secondary"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  CANCEL_REQUESTED: "warning",
  CANCELLED: "secondary",
};

/** 처리자·처리 일시 줄 이름: 반려면 반려, 그 밖에는 승인 */
function processedLabel(status: LeaveRequestStatus): string {
  return status === "REJECTED" ? "반려" : "승인";
}

/**
 * 휴가 한 건의 상세(내 휴가 목록에서 줄을 누르면): 종류·기간·일수, 상태, 처리자, 사유와 반려·취소 사유.
 * 목록에는 한 줄로 들어가는 것만 두고 길어질 수 있는 사유는 여기서 본다.
 */
export default function LeaveDetailDialog({ leave, onClose }: { leave: LeaveRequest; onClose: () => void }) {
  const period = leave.startDate === leave.endDate ? leave.startDate : `${leave.startDate} ~ ${leave.endDate}`;
  const split = formatPeriodSplit(leave);
  const processed = leave.approverName != null && leave.status !== "PENDING";
  const rows: { label: string; value: React.ReactNode }[] = [
    {
      label: "종류",
      value: (
        <span className="inline-flex items-center gap-2">
          <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: leave.leaveTypeColor }} />
          {leave.leaveTypeName}
          {leave.specialRuleName && <span className="text-muted-foreground">{formatSpecialRule(leave)}</span>}
        </span>
      ),
    },
    { label: "기간", value: period },
    {
      label: "일수",
      value: (
        <>
          {formatLeaveAmount(leave)}
          {split && <span className="block text-xs text-muted-foreground">{split}</span>}
          {leave.forfeitedDays > 0 && (
            <span className="block text-xs text-muted-foreground">연차 {formatDays(leave.forfeitedDays)}일 소멸</span>
          )}
        </>
      ),
    },
    { label: "상태", value: <Badge variant={STATUS_VARIANT[leave.status]}>{LEAVE_STATUS_LABEL[leave.status]}</Badge> },
    { label: "신청 일시", value: formatDateTime(leave.createdAt) },
    { label: "신청 사유", value: leave.reason || "-" },
    {
      label: "처리자",
      value: processed ? `${leave.approverName} (${processedLabel(leave.status)})` : "아직 처리 전",
    },
  ];
  if (processed && leave.approvedAt) {
    rows.push({ label: `${processedLabel(leave.status)} 일시`, value: formatDateTime(leave.approvedAt) });
  }
  if (leave.status === "REJECTED") rows.push({ label: "반려 사유", value: leave.rejectReason || "-" });
  if (leave.status === "CANCELLED" || leave.status === "CANCEL_REQUESTED") {
    rows.push({ label: "취소 사유", value: leave.cancelReason || "-" });
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>휴가 상세</DialogTitle>
          <DialogDescription>
            {leave.leaveTypeName} · {period}
          </DialogDescription>
        </DialogHeader>
        <dl className="divide-y rounded-md border text-sm">
          {rows.map((r) => (
            <div key={r.label} className="grid grid-cols-[96px_1fr] gap-3 px-3 py-2">
              <dt className="text-muted-foreground">{r.label}</dt>
              <dd className="min-w-0 whitespace-pre-wrap break-words">{r.value}</dd>
            </div>
          ))}
        </dl>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
