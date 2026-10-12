import type { LeaveRequest } from "@/types";

function reasonText(leave: LeaveRequest): string | null {
  if (leave.status === "REJECTED") return leave.rejectReason ? `반려 사유: ${leave.rejectReason}` : null;
  if ((leave.status === "CANCELLED" || leave.status === "CANCEL_REQUESTED") && leave.cancelReason) {
    return `취소 사유: ${leave.cancelReason}`;
  }
  return null;
}

/** 상태 배지 아래에 반려·취소 사유(반려 알림의 링크로 내 휴가에 와도 사유를 볼 수 있게) */
export default function StatusReason({ leave }: { leave: LeaveRequest }) {
  const text = reasonText(leave);
  if (!text) return null;
  return <p className="mt-1 max-w-[220px] whitespace-normal break-words text-xs text-muted-foreground">{text}</p>;
}
