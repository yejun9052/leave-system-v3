import { useEffect, useRef } from "react";

/** 글자를 쓰는 칸이면 ←/→ 는 커서 이동에 쓴다. */
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  return !!target.closest("input, textarea, select, [contenteditable]");
}

/** ←/→ 를 스스로 쓰는 부품(탭 목록, 라디오 묶음, 메뉴, 선택 목록, 슬라이더) 안에 있으면 그쪽에 맡긴다. */
function isInsideArrowWidget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    !!target.closest('[role="tablist"], [role="radiogroup"], [role="menu"], [role="listbox"], [role="slider"]')
  );
}

/**
 * 열린 모달 창·확인 창, 펼친 드롭다운·선택 목록이 있으면 화면 뒤쪽을 넘기지 않는다.
 * 캘린더의 휴가 신청 패널은 모달이 아니어서(aria-modal="false") 열려 있어도 달을 넘길 수 있다.
 */
function hasOpenOverlay(): boolean {
  return !!document.querySelector(
    '[role="dialog"][data-state="open"], [role="alertdialog"], [aria-modal="true"], [data-radix-popper-content-wrapper]',
  );
}

/**
 * ←/→ 키로 이전·다음(달, 페이지)으로 넘긴다. 이전·다음이 있는 화면에서 함께 쓴다.
 * 입력 칸에 쓰는 중이거나 창이 열려 있을 때, Alt·Ctrl·Shift·Meta 와 함께 누를 때(브라우저 뒤로 가기 등)는 넘기지 않는다.
 * 넘길 수 없는 끝(첫 페이지에서 ←)이면 onPrev/onNext 에 null 을 넘긴다.
 */
export function useArrowKeyNavigation({
  onPrev,
  onNext,
  enabled = true,
}: {
  onPrev: (() => void) | null;
  onNext: (() => void) | null;
  enabled?: boolean;
}) {
  // 매번 새로 만들어지는 콜백이어도 리스너를 다시 달지 않게 최신 값만 들고 있는다
  const handlers = useRef({ onPrev, onNext });
  handlers.current = { onPrev, onNext };

  useEffect(() => {
    if (!enabled) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      if (e.defaultPrevented || e.repeat || e.altKey || e.ctrlKey || e.shiftKey || e.metaKey) return;
      if (isTyping(e.target) || isInsideArrowWidget(e.target) || hasOpenOverlay()) return;
      const action = e.key === "ArrowLeft" ? handlers.current.onPrev : handlers.current.onNext;
      if (!action) return;
      e.preventDefault();
      action();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [enabled]);
}
