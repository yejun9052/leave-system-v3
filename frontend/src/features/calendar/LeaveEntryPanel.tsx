import { useEffect, useRef, useState } from "react";
import { CalendarRange, GripHorizontal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
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
 * 캘린더에서 바로 쓰는 휴가 신청 패널. 입력·검증은 "내 휴가" 신청 다이얼로그와 같은 폼을 쓴다.
 * <ul>
 *   <li>floating(데스크톱): 달력을 가리지 않게 떠 있고 제목줄을 끌어 옮긴다. 모달이 아니라 열린 채로 날짜를 계속 누를 수 있다</li>
 *   <li>sheet(모바일): 화면 아래에서 올라오는 시트. "날짜 바꾸기"로 달력에서 기간을 다시 고른다</li>
 * </ul>
 * 날짜 선택 모드 동안에는 hidden 으로 숨기기만 해서 입력 중인 내용을 유지한다.
 */
export default function LeaveEntryPanel({
  selection,
  variant = "floating",
  hidden = false,
  onChangeDates,
  onClose,
  onSaved,
  onPartialChange,
}: {
  selection: DateSelection;
  variant?: "floating" | "sheet";
  hidden?: boolean;
  /** 모바일: 날짜 선택 모드로 들어가기 */
  onChangeDates?: () => void;
  onClose: () => void;
  onSaved: () => void;
  /** 반차·반반차·시간차를 고르거나 해제할 때(달력이 하루 선택으로 바꾸도록) */
  onPartialChange: (partial: boolean) => void;
}) {
  const sheet = variant === "sheet";
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
  const hiddenRef = useRef(hidden);
  hiddenRef.current = hidden;
  useEffect(() => {
    // 캡처 단계에서 먼저 확인해야 선택 목록이 닫히기 전의 상태를 볼 수 있다
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !hiddenRef.current && !hasOpenOverlay()) closeRef.current();
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

  // 끄는 동안에는 window 에서 이동을 받는다(포인터 캡처가 안 잡히는 환경에서도 제목줄 밖으로 빨리 움직여도 따라오게)
  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    e.preventDefault(); // 끄는 동안 달력 글자가 선택되지 않게
    drag.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y };
    const onMove = (ev: PointerEvent) => {
      if (!drag.current) return;
      setPos(clampPosition(ev.clientX - drag.current.dx, ev.clientY - drag.current.dy));
    };
    const onUp = () => {
      drag.current = null;
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  };

  const { start, end } = selection;
  const hint = form.isPartial
    ? sheet
      ? "반차·반반차·시간차는 하루만 신청합니다."
      : "반차·반반차·시간차는 하루만 신청합니다. 다른 날짜를 누르면 그 날로 바뀝니다."
    : sheet
      ? end === null
        ? "이대로 신청하면 이 하루만 신청합니다."
        : null
      : end === null
        ? "달력에서 종료일을 누르면 기간으로 신청합니다. 이대로 신청하면 이 하루만 신청합니다."
        : "다른 날짜를 누르면 새로 선택합니다.";

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-label="휴가 신청"
      className={cn(
        "fixed z-40 flex flex-col border bg-background shadow-xl",
        sheet ? "inset-x-0 bottom-0 max-h-[85dvh] rounded-t-xl" : "rounded-lg",
        hidden && "hidden",
      )}
      style={
        sheet
          ? undefined
          : { left: pos.x, top: pos.y, width: PANEL_WIDTH, maxWidth: "calc(100vw - 16px)", maxHeight: `calc(100dvh - ${pos.y + 8}px)` }
      }
    >
      <div
        className={cn(
          "flex select-none items-center gap-2 border-b px-4 py-3",
          !sheet && "cursor-move touch-none",
        )}
        onPointerDown={sheet ? undefined : onPointerDown}
      >
        {!sheet && <GripHorizontal className="h-4 w-4 text-muted-foreground" />}
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
              <div className="flex items-center gap-2">
                <p className="flex-1 text-sm font-medium">
                  {formatDateWithWeekday(start)}
                  {!form.isPartial && end !== null && end !== start && ` ~ ${formatDateWithWeekday(end)}`}
                </p>
                {onChangeDates && (
                  <Button type="button" size="sm" variant="outline" onClick={onChangeDates}>
                    <CalendarRange className="h-4 w-4" /> 날짜 바꾸기
                  </Button>
                )}
              </div>
              {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
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
