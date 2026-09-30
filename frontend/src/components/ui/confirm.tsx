import * as React from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export interface ConfirmOptions {
  title: string;
  /** 줄바꿈(\n)은 그대로 표시된다 */
  description?: string;
  confirmText?: string;
  cancelText?: string;
  /** 삭제·퇴사처럼 되돌리기 어려운 작업이면 빨간 확인 버튼 */
  destructive?: boolean;
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = React.createContext<ConfirmFn | null>(null);

type Pending = ConfirmOptions & { resolve: (ok: boolean) => void };

/**
 * 브라우저 confirm() 대신 쓰는 확인 모달. 앱 최상단에 한 번 둔다.
 * 사용: const confirm = useConfirm(); if (!(await confirm({ title: "저장할까요?" }))) return;
 */
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [pending, setPending] = React.useState<Pending | null>(null);

  const confirm = React.useCallback<ConfirmFn>(
    (options) =>
      new Promise<boolean>((resolve) => {
        setPending({ ...options, resolve });
      }),
    [],
  );

  const close = (ok: boolean) => {
    pending?.resolve(ok);
    setPending(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={pending !== null} onOpenChange={(open) => !open && close(false)}>
        {pending && (
          <DialogContent className="max-w-md" hideClose>
            <DialogHeader>
              <DialogTitle>{pending.title}</DialogTitle>
              {pending.description && (
                <DialogDescription className="whitespace-pre-line">{pending.description}</DialogDescription>
              )}
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => close(false)}>
                {pending.cancelText ?? "취소"}
              </Button>
              <Button
                variant={pending.destructive ? "destructive" : "default"}
                onClick={() => close(true)}
                autoFocus
              >
                {pending.confirmText ?? "확인"}
              </Button>
            </DialogFooter>
          </DialogContent>
        )}
      </Dialog>
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): ConfirmFn {
  const confirm = React.useContext(ConfirmContext);
  if (!confirm) {
    throw new Error("useConfirm 은 ConfirmProvider 안에서 사용해야 합니다.");
  }
  return confirm;
}
