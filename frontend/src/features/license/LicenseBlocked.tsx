import { ShieldAlert } from "lucide-react";
import type { LicenseStatus } from "@/api/license";

export default function LicenseBlocked({ status }: { status: LicenseStatus }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-md rounded-xl border bg-white p-8 text-center shadow-sm">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-destructive/10">
          <ShieldAlert className="h-7 w-7 text-destructive" />
        </div>
        <h1 className="text-xl font-bold">라이선스가 유효하지 않습니다</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          시스템을 사용할 수 없습니다. 아래 사유를 확인 후 관리자(공급사)에게 문의하세요.
        </p>
        <div className="mt-4 rounded-lg bg-muted p-3 text-sm">
          사유: {status.reason || "라이선스 확인 실패"}
        </div>
        {status.licensee && (
          <p className="mt-3 text-xs text-muted-foreground">라이선스 대상: {status.licensee}</p>
        )}
      </div>
    </div>
  );
}
