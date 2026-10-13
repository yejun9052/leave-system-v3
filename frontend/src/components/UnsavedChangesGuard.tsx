import { useEffect, useRef } from "react";
import { useBlocker } from "react-router-dom";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useUnsavedChangesStore } from "@/store/unsavedChanges";

/**
 * 저장하지 않은 설정(useUnsavedChanges)이 있을 때 화면을 떠나면 "변경사항이 있습니다. 저장하시겠습니까?"를 묻는다.
 * - 화면 안 이동(메뉴·뒤로가기): 라우터가 이동을 멈추고 이 창을 띄운다. 같은 화면의 탭 이동(?tab=)은 묻지 않는다
 * - 로그아웃: AppLayout 이 confirmLeave() 로 먼저 묻는다
 * - 브라우저 닫기·새로고침: 브라우저 자체 경고(문구는 바꿀 수 없다)
 * 앱 최상단에 한 번 둔다.
 */
export default function UnsavedChangesGuard() {
  const entries = useUnsavedChangesStore((s) => s.entries);
  const prompt = useUnsavedChangesStore((s) => s.prompt);
  const answer = useUnsavedChangesStore((s) => s.answer);
  const confirmLeave = useUnsavedChangesStore((s) => s.confirmLeave);
  const dirty = Object.keys(entries).length > 0;

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      Object.keys(useUnsavedChangesStore.getState().entries).length > 0
      && currentLocation.pathname !== nextLocation.pathname,
  );

  const blockerRef = useRef(blocker);
  blockerRef.current = blocker;
  // 멈춘 순간에 한 번만 묻는다(창이 뜨며 다시 그려져도 다시 묻지 않게 상태만 본다)
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    void confirmLeave().then((leave) => {
      const b = blockerRef.current;
      if (b.state !== "blocked") return;
      if (leave) b.proceed();
      else b.reset();
    });
  }, [blocker.state, confirmLeave]);

  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  const labels = Object.values(entries).map((e) => e.label);

  return (
    <Dialog open={prompt !== null} onOpenChange={(open) => !open && void answer("cancel")}>
      {prompt && (
        <DialogContent className="max-w-md" hideClose>
          <DialogHeader>
            <DialogTitle>변경사항이 있습니다. 저장하시겠습니까?</DialogTitle>
            <DialogDescription>저장하지 않은 설정: {labels.join(", ")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => void answer("cancel")}>
              취소
            </Button>
            <Button variant="outline" onClick={() => void answer("discard")}>
              저장 안 함
            </Button>
            <Button onClick={() => void answer("save")} autoFocus>
              저장
            </Button>
          </DialogFooter>
        </DialogContent>
      )}
    </Dialog>
  );
}
