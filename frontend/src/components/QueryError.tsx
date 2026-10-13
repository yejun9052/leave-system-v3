import { CircleAlert, RotateCw } from "lucide-react";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * 조회가 실패했을 때의 안내. "없음"이나 "불러오는 중…"으로 보이면 데이터가 없다고 오해하므로 오류임을 알리고 다시 시도하게 한다.
 */
export default function QueryError({ error, onRetry, className }: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-wrap items-center gap-2 rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-sm text-destructive",
        className,
      )}
    >
      <CircleAlert className="h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">{extractErrorMessage(error, "불러오지 못했습니다. 잠시 후 다시 시도해 주세요.")}</span>
      {onRetry && (
        <Button type="button" variant="outline" size="sm" className="h-7" onClick={onRetry}>
          <RotateCw className="h-3.5 w-3.5" /> 다시 시도
        </Button>
      )}
    </div>
  );
}
