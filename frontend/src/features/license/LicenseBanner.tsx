import { useQuery } from "@tanstack/react-query";
import { AlertTriangle } from "lucide-react";
import { licenseApi } from "@/api/license";

/** 라이선스 만료 임박(30일 이하) 시 상단 경고 배너. */
export default function LicenseBanner() {
  const { data } = useQuery({ queryKey: ["license"], queryFn: licenseApi.status });
  if (!data || !data.enforced || !data.valid) return null;
  if (data.daysLeft > 30) return null;

  return (
    <div className="flex items-center gap-2 bg-amber-100 px-4 py-2 text-sm text-amber-800 lg:px-8">
      <AlertTriangle className="h-4 w-4 shrink-0" />
      <span>
        라이선스 만료 <b>D-{data.daysLeft}</b> ({data.expiresAt}) — 갱신이 필요합니다. 공급사에 문의하세요.
      </span>
    </div>
  );
}
