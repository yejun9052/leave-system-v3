import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import BlackoutSaveDialog from "@/features/policy/BlackoutSaveDialog";
import { LeaveRegisterFields, useLeaveRegisterForm } from "@/features/leave/LeaveRegisterDialog";
import EntryPanel, { SelectionPeriod } from "./EntryPanel";
import type { DateSelection } from "./useDateSelection";

interface PanelProps {
  selection: DateSelection;
  variant?: "floating" | "sheet";
  hidden?: boolean;
  /** 모바일: 날짜 선택 모드로 들어가기 */
  onChangeDates?: () => void;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * 캘린더에서 고른 기간으로 연차 사용 금지 기간(블랙아웃) 등록(인사관리자·시스템 관리자).
 * "다음"을 누르면 정책 화면과 같은 확인 창에서 겹치는 휴가와 처리 결과를 보여 주고 저장한다.
 */
export function BlackoutEntryPanel({ selection, variant = "floating", hidden, onChangeDates, onClose, onSaved }: PanelProps) {
  const [name, setName] = useState("");
  const [confirming, setConfirming] = useState(false);
  const end = selection.end ?? selection.start;
  return (
    <>
      <EntryPanel
        title="블랙아웃(연차 사용 금지 기간) 등록"
        variant={variant}
        hidden={hidden || confirming}
        onClose={onClose}
        footer={
          <>
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button onClick={() => setConfirming(true)} disabled={!name.trim()}>
              다음
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <SelectionPeriod selection={selection} sheet={variant === "sheet"} onChangeDates={onChangeDates} />
          <div className="space-y-2">
            <Label htmlFor="blackout-panel-name">명칭</Label>
            <Input
              id="blackout-panel-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="예: 연말 결산 기간"
              autoFocus
            />
          </div>
          <p className="text-xs text-muted-foreground">
            이 기간에는 연차를 신청할 수 없습니다. 겹치는 기존 휴가는 정책(연차 정책 › 사용 통제)대로 처리하며, 저장 전에
            명단을 보여 드립니다.
          </p>
        </div>
      </EntryPanel>
      {confirming && (
        <BlackoutSaveDialog
          initial={{ startDate: selection.start, endDate: end, name: name.trim() }}
          onSaved={onSaved}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  );
}

/**
 * 캘린더에서 고른 기간으로 휴가 직접 등록(인사관리자·시스템 관리자). 입력은 결재함의 "휴가 직접 등록" 창과 같다.
 * 반차·시간차처럼 하루만 등록하는 종류를 고르면 달력 선택을 그 하루로 줄인다.
 */
export function RegisterEntryPanel({
  selection,
  variant = "floating",
  hidden,
  onChangeDates,
  onClose,
  onSaved,
  onPartialChange,
}: PanelProps & { onPartialChange: (partial: boolean) => void }) {
  const form = useLeaveRegisterForm({
    start: selection.start,
    end: selection.end ?? selection.start,
    onDone: onSaved,
  });

  const partialChangeRef = useRef(onPartialChange);
  partialChangeRef.current = onPartialChange;
  useEffect(() => {
    partialChangeRef.current(form.isPartial);
  }, [form.isPartial]);

  return (
    <EntryPanel
      title="휴가 강제 등록"
      variant={variant}
      hidden={hidden}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={form.submit} disabled={!form.canSubmit}>
            {form.pending && <Loader2 className="h-4 w-4 animate-spin" />} 등록
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-xs text-muted-foreground">
          다른 직원의 휴가를 결재 없이 바로 승인 상태로 등록합니다. 지난 날짜도 되고, 시작일은 근무일이어야 합니다.
        </p>
        <LeaveRegisterFields
          form={form}
          dates={
            <SelectionPeriod
              selection={selection}
              single={form.isPartial}
              sheet={variant === "sheet"}
              onChangeDates={onChangeDates}
            />
          }
        />
      </div>
    </EntryPanel>
  );
}
