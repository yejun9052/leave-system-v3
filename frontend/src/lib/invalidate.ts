import type { QueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";

/**
 * 휴가·잔여 연차가 바뀌었을 때(신청·취소·승인·반려·직접 등록·강제 취소, 금지 기간 저장, 전 직원 연차 부여,
 * 공휴일 동기화, 정책 저장) 다시 불러올 데이터. 화면에 떠 있는 것만 바로 다시 요청된다.
 * 내 탭이 바꾼 것은 실시간 신호로 다시 오지 않으므로(useRealtime) 바꾼 화면이 여기서 함께 새로 받아야
 * 늘 떠 있는 사이드바 요약 등이 옛 값으로 남지 않는다.
 */
const LEAVE_KEYS = [
  "dashboard",
  "myRequests",
  "myBalance",
  "calendarEvents",
  "calendarDay",
  "pendingApprovals",
  "leaveList",
  "eligibility",
  "leavePreview",
  "notifications",
  "promotionTargets",
];

export function invalidateLeaveData(qc: QueryClient) {
  for (const key of LEAVE_KEYS) void qc.invalidateQueries({ queryKey: [key] });
}

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
