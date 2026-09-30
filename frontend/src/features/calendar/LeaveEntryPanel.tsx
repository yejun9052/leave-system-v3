import { useEffect, useRef, useState } from "react";
import { GripHorizontal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { LeaveRequestFields, useLeaveRequestForm } from "@/features/leave/LeaveRequestForm";
import { formatDateWithWeekday, type DateSelection } from "./useDateSelection";

const PANEL_WIDTH = 400;

/** 열린 선택 목록·메뉴·모달이 있으면 Esc 는 그쪽을 닫는 데 쓰고 패널은 닫지 않는다. */
function hasOpenOverlay(): boolean {
  return !!document.querySelector('[data-radix-popper-content-wrapper], [role="dialog"][data-state="open"]');
}

function clampPosition(x: number, y: number) {
  return {
    x: Math.min(Math.max(8, x), window.innerWidth - 120),
    y: Math.min(Math.max(8, y), window.innerHeight - 60),
  };
}

/**
 * 캘린더에서 바로 쓰는 휴가 신청 패널(데스크톱). 달력을 가리지 않게 떠 있고 제목줄을 끌어 옮길 수 있다.
 * 모달이 아니라서 열린 채로 달력 날짜를 계속 누를 수 있다. 입력·검증은 "내 휴가" 신청 다이얼로그와 같은 폼을 쓴다.
 */
export default function LeaveEntryPanel({
  selection,
  onClose,
  onSaved,
  onPartialChange,
}: {
  selection: DateSelection;
  onClose: () => void;
  onSaved: () => void;
  /** 반차·반반차·시간차를 고르거나 해제할 때(달력이 하루 선택으로 바꾸도록) */
  onPartialChange: (partial: boolean) => void;
}) {
  const form = useLeaveRequestForm({
    start: selection.start,
    end: selection.end ?? selection.start,
    rememberType: true,
    onSaved,
  });

  const partialChangeRef = useRef(onPartialChange);
  partialChangeRef.current = onPartialChange;
  useEffect(() => {
    partialChangeRef.current(form.isPartial);
  }, [form.isPartial]);

  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    // 캡처 단계에서 먼저 확인해야 선택 목록이 닫히기 전의 상태를 볼 수 있다
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !hasOpenOverlay()) closeRef.current();
    };
    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, []);

  const [pos, setPos] = useState(() => clampPosition(window.innerWidth - PANEL_WIDTH - 32, 96));
  const drag = useRef<{ dx: number; dy: number } | null>(null);

  useEffect(() => {
    const onResize = () => setPos((p) => clampPosition(p.x, p.y));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    drag.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y };
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // 포인터 캡처를 지원하지 않아도 이동 중 좌표로 계속 옮길 수 있다
    }
  };
  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!drag.current) return;
    setPos(clampPosition(e.clientX - drag.current.dx, e.clientY - drag.current.dy));
  };
  const endDrag = () => {
    drag.current = null;
  };

  const { start, end } = selection;
  const hint = form.isPartial
    ? "반차·반반차·시간차는 하루만 신청합니다. 다른 날짜를 누르면 그 날로 바뀝니다."
    : end === null
      ? "달력에서 종료일을 누르면 기간으로 신청합니다. 이대로 신청하면 이 하루만 신청합니다."
      : "다른 날짜를 누르면 새로 선택합니다.";

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-label="휴가 신청"
      className="fixed z-40 flex flex-col rounded-lg border bg-background shadow-xl"
      style={{ left: pos.x, top: pos.y, width: PANEL_WIDTH, maxWidth: "calc(100vw - 16px)", maxHeight: `calc(100dvh - ${pos.y + 8}px)` }}
    >
      <div
        className="flex cursor-move touch-none select-none items-center gap-2 border-b px-4 py-3"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      >
        <GripHorizontal className="h-4 w-4 text-muted-foreground" />
        <h2 className="flex-1 font-semibold">휴가 신청</h2>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={onClose}
          title="닫기 (Esc)"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto px-4 py-4">
        <LeaveRequestFields
          form={form}
          dates={
            <div className="space-y-1">
              <Label>기간</Label>
              <p className="text-sm font-medium">
                {formatDateWithWeekday(start)}
                {!form.isPartial && end !== null && end !== start && ` ~ ${formatDateWithWeekday(end)}`}
              </p>
              <p className="text-xs text-muted-foreground">{hint}</p>
            </div>
          }
        />
      </div>
      <div className="flex justify-end gap-2 border-t px-4 py-3">
        <Button variant="outline" onClick={onClose}>
          취소
        </Button>
        <Button onClick={form.submit} disabled={!form.canSubmit}>
          신청
        </Button>
      </div>
    </div>
  );
}
