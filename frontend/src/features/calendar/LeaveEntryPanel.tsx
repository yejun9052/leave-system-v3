import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { LeaveRequestFields, useLeaveRequestForm } from "@/features/leave/LeaveRequestForm";
import type { DateSpan } from "@/lib/dateSpans";
import EntryPanel, { SelectionPeriod } from "./EntryPanel";

/**
 * 캘린더에서 바로 쓰는 휴가 신청 패널. 입력·검증은 "내 휴가" 신청 다이얼로그와 같은 폼을 쓴다.
 * 고른 날짜는 이어진 묶음(spans)으로 받고, 묶음마다 신청 한 건이 된다.
 * 패널 틀(떠 있는 창·모바일 시트, 끌어 옮기기, Esc)은 {@link EntryPanel}.
 */
export default function LeaveEntryPanel({
  spans,
  variant = "floating",
  hidden = false,
  onChangeDates,
  onClose,
  onSaved,
  onPartialChange,
  heading,
}: {
  spans: DateSpan[];
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
  const form = useLeaveRequestForm({ spans, rememberType: true, onSaved });

  const partialChangeRef = useRef(onPartialChange);
  partialChangeRef.current = onPartialChange;
  useEffect(() => {
    partialChangeRef.current(form.isPartial);
  }, [form.isPartial]);

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
            {form.sendSpans.length > 1 ? `${form.sendSpans.length}건 신청` : "신청"}
          </Button>
        </>
      }
    >
      <LeaveRequestFields
        form={form}
        dates={
          <SelectionPeriod
            spans={form.sendSpans}
            single={form.isPartial}
            sheet={variant === "sheet"}
            splitNote={`떨어진 날짜는 나눠서 ${form.sendSpans.length}건으로 신청합니다. 결재는 건마다 따로 합니다.`}
            onChangeDates={onChangeDates}
          />
        }
      />
    </EntryPanel>
  );
}
