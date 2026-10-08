import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, ChevronRight } from "lucide-react";
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

type AppNotification = Awaited<ReturnType<typeof notificationApi.list>>[number];

/** 알림을 눌렀을 때 갈 곳과 버튼 이름. 결재 요청·취소 요청은 눈에 띄게 보여 준다. */
function actionOf(n: AppNotification): { to: string; label: string; primary?: boolean } | null {
  // 서버가 정한 앱 안의 주소만 따라간다
  if (!n.link || !n.link.startsWith("/") || n.link.startsWith("//")) return null;
  if (n.type === "LEAVE_REQUESTED" || n.type === "LEAVE_CANCEL_REQUESTED") {
    return { to: n.link, label: "결재하러 가기", primary: true };
  }
  if (n.type.startsWith("BACKUP_")) return { to: "/admin/policy?tab=backup", label: "백업 확인하기" };
  switch (n.link) {
    case "/approvals":
      return { to: n.link, label: "결재함 보기" };
    case "/my-leaves":
      return { to: n.link, label: "내 휴가 확인하기" };
    case "/calendar":
      return { to: n.link, label: "캘린더에서 보기" };
    case "/admin/policy":
      return { to: n.link, label: "정책 보기" };
    default:
      return { to: n.link, label: "열기" };
  }
}

/**
 * 종 아이콘과 알림 목록. 열림 상태는 바깥(상단 안내 "확인하기")에서도 열 수 있게 받는다.
 * 목록을 열면 모두 읽음으로 바꾼다. 알림 줄을 누르면 그 알림의 화면으로 가고 목록을 닫는다.
 */
export default function NotificationBell({ open, onOpenChange }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const navigate = useNavigate();
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
              items.map((n) => {
                const action = actionOf(n);
                const body = (
                  <>
                    <p className="font-medium">{n.title}</p>
                    {n.message && <p className="text-xs text-muted-foreground">{n.message}</p>}
                    <div className="mt-1.5 flex items-center justify-between gap-2">
                      <span className="text-[11px] text-muted-foreground">{formatDateTime(n.createdAt)}</span>
                      {action && (
                        <span
                          className={cn(
                            "inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold",
                            action.primary
                              ? "bg-primary text-primary-foreground group-hover:bg-primary/90"
                              : "bg-muted text-foreground/80 group-hover:bg-muted-foreground/20",
                          )}
                        >
                          {action.label} <ChevronRight className="h-3.5 w-3.5" />
                        </span>
                      )}
                    </div>
                  </>
                );
                return (
                  <li key={n.id} className={cn("border-b text-sm last:border-0", !n.read && "bg-accent/40")}>
                    {action ? (
                      // 줄 전체가 버튼 하나(안에 버튼을 또 두지 않는다)
                      <button
                        type="button"
                        onClick={() => {
                          setOpen(false);
                          navigate(action.to);
                        }}
                        className="group block w-full px-4 py-3 text-left transition-colors hover:bg-accent"
                      >
                        {body}
                      </button>
                    ) : (
                      <div className="px-4 py-3">{body}</div>
                    )}
                  </li>
                );
              })
            ) : (
              <li className="px-4 py-8 text-center text-sm text-muted-foreground">알림이 없습니다.</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
