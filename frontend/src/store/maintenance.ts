import { create } from "zustand";

interface MaintenanceState {
  /** 서버가 점검 중(503 MAINTENANCE)이라고 답했는지 */
  active: boolean;
  /** 이 화면에서 직접 복원을 실행 중인지(복원 창이 진행 상태를 보여 주므로 점검 안내는 띄우지 않음) */
  selfRestoring: boolean;
  setActive: (active: boolean) => void;
  setSelfRestoring: (restoring: boolean) => void;
}

/** 시스템 점검(복원 중) 안내 상태. api/client 가 503 MAINTENANCE 를 받으면 켠다. */
export const useMaintenanceStore = create<MaintenanceState>((set) => ({
  active: false,
  selfRestoring: false,
  setActive: (active) => set({ active }),
  setSelfRestoring: (selfRestoring) => set({ selfRestoring }),
}));
