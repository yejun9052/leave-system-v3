import { LogOut } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { ChangePasswordForm } from "./ChangePasswordForm";

/**
 * 비밀번호 변경이 필요한 사용자(임시 비밀번호·기존 계정 이관)에게 띄우는 창.
 * 닫기 버튼·ESC·바깥 클릭으로 닫을 수 없다. 서버도 변경 전 다른 API 를 403 으로 막으므로
 * 이 창을 개발자도구로 지워도 기능을 쓸 수 없다. 나갈 수 있는 방법은 로그아웃뿐.
 */
export default function ForcePasswordChangeDialog() {
  const { loadMe, logout } = useAuthStore();
  const navigate = useNavigate();
  const { toast } = useToast();

  const onChanged = async () => {
    toast({ title: "비밀번호가 변경되었습니다.", variant: "success" });
    await loadMe(); // passwordChangeRequired=false 로 갱신 → 창 닫힘
  };

  const onLogout = async () => {
    await logout().catch(() => undefined);
    navigate("/login", { replace: true });
  };

  return (
    <Dialog open onOpenChange={() => undefined}>
      <DialogContent
        hideClose
        className="max-w-sm"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onPointerDownOutside={(e) => e.preventDefault()}
        onInteractOutside={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>비밀번호를 변경하세요</DialogTitle>
          <DialogDescription>
            임시 비밀번호로 로그인했거나 비밀번호 정책이 바뀌었습니다. 새 비밀번호로 변경해야 계속 이용할 수
            있습니다.
          </DialogDescription>
        </DialogHeader>
        <ChangePasswordForm
          currentLabel="현재(임시) 비밀번호"
          submitLabel="변경하고 계속하기"
          onChanged={onChanged}
        />
        <Button variant="ghost" className="w-full" onClick={onLogout}>
          <LogOut className="h-4 w-4" />
          로그아웃
        </Button>
      </DialogContent>
    </Dialog>
  );
}
