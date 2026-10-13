import { create } from "zustand";

/** 저장하지 않은 설정 하나. save 는 저장에 성공하면 true(확인 창에서 취소했거나 입력이 잘못됐으면 false). */
export interface UnsavedEntry {
  label: string;
  save: () => Promise<boolean>;
}

export type LeaveChoice = "save" | "discard" | "cancel";

interface UnsavedChangesState {
  /** 화면에 떠 있는 설정 중 저장하지 않은 것(id → 설정) */
  entries: Record<string, UnsavedEntry>;
  /** "변경사항이 있습니다. 저장하시겠습니까?" 창이 기다리는 답 */
  prompt: { resolve: (leave: boolean) => void } | null;
  register: (id: string, entry: UnsavedEntry) => void;
  unregister: (id: string) => void;
  /** 화면을 떠나도 되는지 묻는다. 저장하지 않은 설정이 없으면 바로 true */
  confirmLeave: () => Promise<boolean>;
  answer: (choice: LeaveChoice) => Promise<void>;
  /** 묻지 않고 버린다(세션 만료·복원처럼 어차피 저장할 수 없는 이동 직전) */
  discardAll: () => void;
}

/**
 * 정책 화면처럼 고친 뒤 저장하는 설정의 "저장 안 한 변경" 목록. 화면 밖으로 나갈 때(메뉴 이동·뒤로가기·로그아웃)
 * UnsavedChangesGuard 가 이 목록을 보고 확인 창을 띄운다.
 */
export const useUnsavedChangesStore = create<UnsavedChangesState>((set, get) => ({
  entries: {},
  prompt: null,
  register: (id, entry) => set((s) => ({ entries: { ...s.entries, [id]: entry } })),
  unregister: (id) =>
    set((s) => {
      if (!(id in s.entries)) return s;
      const { [id]: _removed, ...rest } = s.entries;
      void _removed;
      return { entries: rest };
    }),
  confirmLeave: () => {
    if (Object.keys(get().entries).length === 0) return Promise.resolve(true);
    get().prompt?.resolve(false);
    return new Promise<boolean>((resolve) => set({ prompt: { resolve } }));
  },
  answer: async (choice) => {
    const prompt = get().prompt;
    if (!prompt) return;
    set({ prompt: null });
    if (choice === "cancel") {
      prompt.resolve(false);
      return;
    }
    if (choice === "save") {
      // 하나라도 저장하지 못하면 머문다(오류는 각 설정이 알림으로 보여 준다)
      for (const entry of Object.values(get().entries)) {
        const ok = await entry.save().catch(() => false);
        if (!ok) {
          prompt.resolve(false);
          return;
        }
      }
    }
    set({ entries: {} });
    prompt.resolve(true);
  },
  discardAll: () => set({ entries: {} }),
}));
