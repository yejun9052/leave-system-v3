package com.company.leave.leave.domain;

public enum LeaveRequestStatus {
    /** 결재 대기(팀장 단계 또는 인사 단계, 단계는 정책·조직으로 판단) */
    PENDING,
    /** 팀장 1차 승인 완료, 인사관리자 최종 승인 대기 */
    LEAD_APPROVED,
    /** 승인됨(최종) */
    APPROVED,
    /** 반려됨 */
    REJECTED,
    /** 승인된 휴가에 대해 취소 요청됨(인사관리자 결재 대기) */
    CANCEL_REQUESTED,
    /** 취소됨 */
    CANCELLED
}
