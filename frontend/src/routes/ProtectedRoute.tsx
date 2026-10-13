import { useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { CircleAlert, Loader2, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/store/auth";
import { roleIncludes, type Role } from "@/types";

export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const location = useLocation();
  const { user, initialized, loadError, loadMe } = useAuthStore();
  const [retrying, setRetrying] = useState(false);

  if (!initialized) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // 로그인 확인이 서버 오류·네트워크로 실패: 로그인이 끊긴 게 아니므로 로그인 화면 대신 다시 시도
  if (!user && loadError) {
    return (
      <div className="flex min-h-dvh items-center justify-center p-4">
        <div role="alert" className="w-full max-w-sm space-y-3 rounded-lg border bg-card p-6 text-center shadow-sm">
          <CircleAlert className="mx-auto h-8 w-8 text-destructive" />
          <p className="text-sm">{loadError}</p>
          <Button
            disabled={retrying}
            onClick={async () => {
              setRetrying(true);
              await loadMe();
              setRetrying(false);
            }}
          >
            {retrying ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCw className="h-4 w-4" />} 다시 시도
          </Button>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (roles && !roles.some((r) => roleIncludes(user.role, r))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
