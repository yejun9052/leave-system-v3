import { Clock } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { IDLE_WARNING_SECONDS, useIdleCountdown, useIdleLogout } from "@/lib/useIdleLogout";
import { cn } from "@/lib/utils";

/**
 * 자동 로그아웃(입력 없이 정해진 시간이 지나면 로그아웃)과 마지막 1분의 안내 창.
 * 마우스를 움직이거나 키를 누르면 시간이 다시 시작되고 창도 닫힌다. 남은 시간은 상단 {@link SessionTimer} 에도 보인다.
 */
export default function IdleLogoutWarning({ timeoutSeconds }: { timeoutSeconds: number | undefined }) {
  useIdleLogout(timeoutSeconds);
  const secondsLeft = useIdleCountdown((s) => s.secondsLeft);
  if (secondsLeft == null || secondsLeft > IDLE_WARNING_SECONDS) return null;
  const minutes = Math.round((timeoutSeconds ?? 0) / 60);

  return (
    // 버튼·바깥을 누르는 것 자체가 입력이라 안내가 사라진다
    <Dialog open onOpenChange={() => undefined}>
      <DialogContent hideClose className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Clock className="h-5 w-5 text-amber-600" />
            곧 자동으로 로그아웃됩니다
          </DialogTitle>
          <DialogDescription>
            {minutes}분 동안 사용하지 않아 <strong className="text-foreground">{secondsLeft}초</strong> 뒤 로그아웃됩니다.
            저장하지 않은 입력은 사라집니다.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button className="w-full sm:w-auto">계속 사용</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** 상단 바의 세션 만료 타이머(남은 시간 "세션 만료 09:10"). 마지막 1분은 주황색 */
export function SessionTimer() {
  const secondsLeft = useIdleCountdown((s) => s.secondsLeft);
  if (secondsLeft == null) return null;
  const warn = secondsLeft <= IDLE_WARNING_SECONDS;
  const time = `${String(Math.floor(secondsLeft / 60)).padStart(2, "0")}:${String(secondsLeft % 60).padStart(2, "0")}`;
  return (
    <div
      role="timer"
      aria-label={`세션 만료까지 ${time}`}
      title="마우스·키보드 입력이 없으면 이 시간이 지난 뒤 자동으로 로그아웃됩니다. 입력하면 다시 시작됩니다."
      className={cn(
        "flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-sm",
        warn ? "border-amber-300 bg-amber-50 font-semibold text-amber-800" : "text-muted-foreground",
      )}
    >
      <Clock className={cn("h-4 w-4", warn && "text-amber-600")} />
      <span className="hidden xl:inline">세션 만료</span>
      <span className="tabular-nums">{time}</span>
    </div>
  );
}
