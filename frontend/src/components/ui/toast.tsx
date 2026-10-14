import * as React from "react";
import * as ToastPrimitive from "@radix-ui/react-toast";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

type ToastVariant = "default" | "success" | "destructive";

interface ToastItem {
  id: number;
  title?: string;
  description?: string;
  variant: ToastVariant;
}

interface ToastContextValue {
  toast: (opts: { title?: string; description?: string; variant?: ToastVariant }) => void;
}

const ToastContext = React.createContext<ToastContextValue | null>(null);

export function useToast() {
  const ctx = React.useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}

let idSeq = 0;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = React.useState<ToastItem[]>([]);

  const toast = React.useCallback(
    (opts: { title?: string; description?: string; variant?: ToastVariant }) => {
      const id = ++idSeq;
      setItems((prev) => [...prev, { id, variant: "default", ...opts }]);
    },
    [],
  );

  const remove = (id: number) => setItems((prev) => prev.filter((t) => t.id !== id));

  /**
   * 알림은 마우스를 올리거나 알림이 포커스를 가지면 사라지는 시간을 멈춘다(Radix). 휴대폰에서 알림을 손가락으로 건드리면
   * 알림이 포커스를 잡은 채 남아 멈춤이 풀리지 않아 계속 떠 있었다. 터치로 누른 뒤에는 포커스를 놓아 시간이 다시 흐르게 한다.
   */
  const viewportRef = React.useRef<HTMLOListElement>(null);
  React.useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;
    let lastPointer = "mouse";
    const onPointerDown = (e: PointerEvent) => {
      lastPointer = e.pointerType;
    };
    const onClick = () => {
      if (lastPointer === "mouse") return;
      const active = document.activeElement;
      if (active instanceof HTMLElement && el.contains(active)) active.blur();
    };
    el.addEventListener("pointerdown", onPointerDown);
    el.addEventListener("click", onClick);
    return () => {
      el.removeEventListener("pointerdown", onPointerDown);
      el.removeEventListener("click", onClick);
    };
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      <ToastPrimitive.Provider swipeDirection="right" duration={4000}>
        {children}
        {items.map((t) => (
          <ToastPrimitive.Root
            key={t.id}
            onOpenChange={(open) => !open && remove(t.id)}
            className={cn(
              "group pointer-events-auto relative flex w-full items-center justify-between gap-3 overflow-hidden rounded-md border p-4 pr-8 shadow-lg transition-all",
              "data-[state=open]:animate-in data-[state=closed]:animate-out data-[swipe=end]:animate-out data-[state=closed]:fade-out-80 data-[state=open]:slide-in-from-top-full",
              t.variant === "destructive" && "border-destructive bg-destructive text-destructive-foreground",
              t.variant === "success" && "border-emerald-500 bg-emerald-500 text-white",
              t.variant === "default" && "border bg-background text-foreground",
            )}
          >
            <div className="grid gap-1">
              {t.title && <ToastPrimitive.Title className="text-sm font-semibold">{t.title}</ToastPrimitive.Title>}
              {t.description && (
                <ToastPrimitive.Description className="text-sm opacity-90">
                  {t.description}
                </ToastPrimitive.Description>
              )}
            </div>
            <ToastPrimitive.Close
              aria-label="닫기"
              className="absolute right-2 top-2 rounded-md p-1 opacity-70 transition-opacity hover:opacity-100"
            >
              <X className="h-4 w-4" />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        ))}
        <ToastPrimitive.Viewport ref={viewportRef} className="fixed top-0 right-0 z-[100] flex max-h-screen w-full flex-col-reverse gap-2 p-4 sm:top-auto sm:bottom-0 sm:right-0 sm:w-96" />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}
