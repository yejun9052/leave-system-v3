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
import { useIdleLogout } from "@/lib/useIdleLogout";

/**
 * 자동 로그아웃(입력 없이 정해진 시간이 지나면 로그아웃)과 마지막 1분의 안내 창.
 * 마우스를 움직이거나 키를 누르면 시간이 다시 시작되고 창도 닫힌다.
 */
export default function IdleLogoutWarning({ timeoutSeconds }: { timeoutSeconds: number | undefined }) {
  const secondsLeft = useIdleLogout(timeoutSeconds);
  if (secondsLeft == null) return null;
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
