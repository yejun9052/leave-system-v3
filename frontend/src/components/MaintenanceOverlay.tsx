import { useEffect } from "react";
import { Loader2, Wrench } from "lucide-react";
import { fetchMaintenance } from "@/api/backups";
import { useMaintenanceStore } from "@/store/maintenance";

const POLL_MS = 3000;

/**
 * 시스템 점검(데이터 복원 중) 안내. 서버가 503 MAINTENANCE 로 답하면 화면 전체를 덮고, 점검이 끝날 때까지
 * 상태를 확인하다가 로그인 화면으로 보낸다(복원하면 모든 로그인 세션이 지워진다).
 * 이 화면에서 직접 복원 중이면 복원 창이 진행 상태를 보여 주므로 띄우지 않는다.
 */
export default function MaintenanceOverlay() {
  const active = useMaintenanceStore((s) => s.active);
  const selfRestoring = useMaintenanceStore((s) => s.selfRestoring);
  const visible = active && !selfRestoring;

  useEffect(() => {
    if (!visible) return;
    let stopped = false;
    const poll = async () => {
      try {
        if (!(await fetchMaintenance())) {
          window.location.href = "/login";
          return;
        }
      } catch {
        // 서버가 잠시 응답하지 않아도 계속 확인
      }
      if (!stopped) timer = window.setTimeout(poll, POLL_MS);
    };
    let timer = window.setTimeout(poll, POLL_MS);
    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  }, [visible]);

  if (!visible) return null;
  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="maintenance-title"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background/95 p-4"
    >
      <div className="w-full max-w-sm space-y-3 rounded-lg border bg-card p-6 text-center shadow-lg">
        <Wrench className="mx-auto h-8 w-8 text-primary" />
        <h2 id="maintenance-title" className="text-lg font-semibold">
          시스템 점검 중입니다
        </h2>
        <p className="text-sm text-muted-foreground">
          관리자가 데이터를 복원하고 있습니다. 끝나면 로그인 화면으로 이동하니 다시 로그인해 주세요.
        </p>
        <p className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> 점검이 끝났는지 확인하는 중…
        </p>
      </div>
    </div>
  );
}
