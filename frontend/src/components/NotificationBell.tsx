import { useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import { notificationApi } from "@/api/leave";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";

/** 확인하지 않은 알림 수(종 배지·상단 안내가 함께 씀). 1분마다 새로 받는다. */
export function useUnreadNotifications(): number {
  const { data } = useQuery({
    queryKey: ["notifications", "count"],
    queryFn: notificationApi.unreadCount,
    refetchInterval: 60_000,
  });
  return data?.count ?? 0;
}

/**
 * 종 아이콘과 알림 목록. 열림 상태는 바깥(상단 안내 "확인하기")에서도 열 수 있게 받는다.
 * 목록을 열면 모두 읽음으로 바꾼다.
 */
export default function NotificationBell({ open, onOpenChange }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const ref = useRef<HTMLDivElement>(null);
  const setOpen = onOpenChange;

  const unread = useUnreadNotifications();
  const { data: items = [] } = useQuery({
    queryKey: ["notifications", "list"],
    queryFn: () => notificationApi.list(20),
    enabled: open,
  });

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [setOpen]);

  // 열면 모두 읽음(종을 누르든 상단 안내를 누르든 같음)
  useEffect(() => {
    if (!open || unread === 0) return;
    void notificationApi.readAll().then(() => qc.invalidateQueries({ queryKey: ["notifications"] }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const toggle = () => setOpen(!open);

  return (
    <div className="relative" ref={ref}>
      <Button variant="ghost" size="icon" onClick={toggle} className="relative" aria-label="알림">
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-bold text-destructive-foreground">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </Button>
      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-lg border bg-popover shadow-lg">
          <div className="border-b px-4 py-2 text-sm font-semibold">알림</div>
          <ul className="max-h-96 overflow-y-auto">
            {items.length > 0 ? (
              items.map((n) => (
                <li
                  key={n.id}
                  className={cn(
                    "border-b px-4 py-3 text-sm last:border-0",
                    !n.read && "bg-accent/40",
                  )}
                >
                  <p className="font-medium">{n.title}</p>
                  {n.message && <p className="text-xs text-muted-foreground">{n.message}</p>}
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {formatDateTime(n.createdAt)}
                  </p>
                </li>
              ))
            ) : (
              <li className="px-4 py-8 text-center text-sm text-muted-foreground">알림이 없습니다.</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
