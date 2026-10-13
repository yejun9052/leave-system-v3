import { RefreshCw, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm";

/**
 * 고치는 중에 다른 세션(다른 사람이나 내 다른 탭)이 같은 설정을 저장했을 때 안내. 내 입력은 그대로 두고,
 * [새로고침]을 누르면 한 번 더 확인한 뒤 내 입력을 버리고 새 값을 불러온다.
 */
export default function ChangedElsewhereNotice({ onReload }: { onReload: () => void }) {
  const confirm = useConfirm();
  const reload = async () => {
    const ok = await confirm({
      title: "새로고침할까요?",
      description: "내가 고친 입력이 사라지고 다른 세션이 저장한 값으로 바뀝니다.",
      confirmText: "새로고침",
    });
    if (ok) onReload();
  };
  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
    >
      <Users className="h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">
        변경사항이 있습니다. 다른 세션에 의한 변경사항이 있습니다. 확인하시겠습니까?
      </span>
      <Button type="button" size="sm" variant="outline" onClick={() => void reload()}>
        <RefreshCw className="h-3.5 w-3.5" /> 새로고침
      </Button>
    </div>
  );
}
