import { useEffect, useRef } from "react";
import { CalendarRange } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { LeaveRequestFields, useLeaveRequestForm } from "@/features/leave/LeaveRequestForm";
import EntryPanel from "./EntryPanel";
import { formatDateWithWeekday, type DateSelection } from "./useDateSelection";

/**
 * 캘린더에서 바로 쓰는 휴가 신청 패널. 입력·검증은 "내 휴가" 신청 다이얼로그와 같은 폼을 쓴다.
 * 패널 틀(떠 있는 창·모바일 시트, 끌어 옮기기, Esc)은 {@link EntryPanel}.
 */
export default function LeaveEntryPanel({
  selection,
  variant = "floating",
  hidden = false,
  onChangeDates,
  onClose,
  onSaved,
  onPartialChange,
  heading,
}: {
  selection: DateSelection;
  /** 관리자: 제목 자리의 할 일 드롭다운(휴가 신청·강제 등록·블랙아웃 등록) */
  heading?: React.ReactNode;
  variant?: "floating" | "sheet";
  hidden?: boolean;
  /** 모바일: 날짜 선택 모드로 들어가기 */
  onChangeDates?: () => void;
  onClose: () => void;
  onSaved: () => void;
  /** 반차·시간차를 고르거나 해제할 때(달력이 하루 선택으로 바꾸도록) */
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

  const { start, end } = selection;
  const hint = form.isPartial
    ? sheet
      ? "반차·시간차는 하루만 신청합니다."
      : "반차·시간차는 하루만 신청합니다. 다른 날짜를 누르면 그 날로 바뀝니다."
    : sheet
      ? end === null
        ? "이대로 신청하면 이 하루만 신청합니다."
        : null
      : end === null
        ? "달력에서 종료일을 누르면 기간으로 신청합니다. 이대로 신청하면 이 하루만 신청합니다."
        : "다른 날짜를 누르면 새로 선택합니다.";

  return (
    <EntryPanel
      title="휴가 신청"
      heading={heading}
      variant={variant}
      hidden={hidden}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={form.submit} disabled={!form.canSubmit}>
            신청
          </Button>
        </>
      }
    >
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
    </EntryPanel>
  );
}
