import { useEffect, useId, useRef } from "react";
import { useUnsavedChangesStore } from "@/store/unsavedChanges";

/**
 * 저장하지 않은 변경이 있는 동안 이 설정을 "나갈 때 확인" 목록에 올린다(UnsavedChangesGuard).
 *
 * @param label 확인 창에 보일 설정 이름(예: "연차 정책")
 * @param dirty 저장하지 않은 변경이 있는지
 * @param save  확인 창에서 "저장"을 누르면 부른다. 저장에 성공하면 true
 */
export function useUnsavedChanges(label: string, dirty: boolean, save: () => Promise<boolean>) {
  const id = useId();
  const saveRef = useRef(save);
  saveRef.current = save;
  const register = useUnsavedChangesStore((s) => s.register);
  const unregister = useUnsavedChangesStore((s) => s.unregister);

  useEffect(() => {
    if (!dirty) return;
    register(id, { label, save: () => saveRef.current() });
    return () => unregister(id);
  }, [id, label, dirty, register, unregister]);
}
