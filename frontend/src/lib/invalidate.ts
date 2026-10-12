import type { QueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";

/**
 * 사용자·부서가 바뀌었을 때(사용자 저장·퇴사·복원·일괄 등록, 부서 추가·수정·이동·삭제) 다시 불러올 데이터.
 * 사용자의 권한·부서가 바뀌면 서버가 부서장(결재자)도 바꾸므로 부서 트리와 결재 경로를 함께 새로 받는다.
 * 내 이름·부서가 바뀌었을 수 있어 로그인한 내 정보도 새로 받는다.
 */
export function invalidateOrgData(qc: QueryClient) {
  for (const key of ["employees", "departments", "approvalRoute", "dashboard", "registerEmployeeSearch"]) {
    void qc.invalidateQueries({ queryKey: [key] });
  }
  void useAuthStore.getState().refreshMe();
}
