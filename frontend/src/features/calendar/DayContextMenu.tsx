import { CalendarPlus, ListChecks } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { formatKoreanDate } from "./useDateSelection";

export interface DayMenuTarget {
  date: string;
  x: number;
  y: number;
}

/**
 * 날짜 칸 우클릭(모바일은 길게 누르기) 메뉴. 누른 위치에 보이지 않는 기준점을 두고 그 아래에 띄운다.
 * 위치가 바뀌면 새로 그리도록 호출하는 쪽에서 key 를 위치로 준다.
 */
export default function DayContextMenu({
  target,
  canApply,
  applyLabel = "이 날짜로 신청하기",
  onApply,
  onDetail,
  onClose,
}: {
  target: DayMenuTarget;
  canApply: boolean;
  /** 관리자가 직접 등록·연차 제한 등록을 고른 경우 문구가 바뀐다 */
  applyLabel?: string;
  onApply: (date: string) => void;
  onDetail: (date: string) => void;
  onClose: () => void;
}) {
  return (
    <DropdownMenu open modal={false} onOpenChange={(open) => !open && onClose()}>
      <DropdownMenuTrigger asChild>
        <span
          aria-hidden
          className="pointer-events-none fixed h-0 w-0"
          style={{ left: target.x, top: target.y }}
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" sideOffset={2} onCloseAutoFocus={(e) => e.preventDefault()}>
        <DropdownMenuLabel>{formatKoreanDate(target.date)}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {canApply && (
          <DropdownMenuItem onSelect={() => onApply(target.date)}>
            <CalendarPlus /> {applyLabel}
          </DropdownMenuItem>
        )}
        <DropdownMenuItem onSelect={() => onDetail(target.date)}>
          <ListChecks /> 일정 상세보기
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
