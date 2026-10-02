import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import { notificationApi } from "@/api/leave";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";

export default function NotificationBell() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const { data: count } = useQuery({
    queryKey: ["notifications", "count"],
    queryFn: notificationApi.unreadCount,
    refetchInterval: 60_000,
  });
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
  }, []);

  const unread = count?.count ?? 0;

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && unread > 0) {
      await notificationApi.readAll();
      qc.invalidateQueries({ queryKey: ["notifications"] });
    }
  };

  return (
    <div className="relative" ref={ref}>
      <Button variant="ghost" size="icon" onClick={toggle} className="relative">
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
