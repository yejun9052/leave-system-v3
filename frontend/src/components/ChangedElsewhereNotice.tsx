import { RefreshCw, Users } from "lucide-react";
import { Button } from "@/components/ui/button";

/** 고치는 중에 다른 사람이 같은 설정을 저장했을 때 안내. 내 입력은 그대로 두고, 원하면 새 값을 불러온다. */
export default function ChangedElsewhereNotice({ onReload }: { onReload: () => void }) {
  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
    >
      <Users className="h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">
        고치는 동안 다른 사람이 이 설정을 바꿨습니다. 지금 저장하면 내 입력으로 덮어씁니다.
      </span>
      <Button type="button" size="sm" variant="outline" onClick={onReload}>
        <RefreshCw className="h-3.5 w-3.5" /> 바뀐 내용 불러오기
      </Button>
    </div>
  );
}
