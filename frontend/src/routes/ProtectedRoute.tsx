import { Navigate, Outlet, useLocation } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import type { Role } from "@/types";

export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const location = useLocation();
  const { user, initialized } = useAuthStore();

  if (!initialized) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !user.roles.some((r) => roles.includes(r))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
