package com.company.leave.leave.domain;

/**
 * 휴가 신청 상태. 결재는 한 번(팀장·인사관리자·시스템 관리자 중 권한 있는 한 명)으로 확정된다.
 * 2단계 결재의 LEAD_APPROVED 는 단일 결재로 바꿀 때 PENDING 으로 되돌리고 없앴다.
 */
public enum LeaveRequestStatus {
    /** 결재 대기 */
    PENDING,
    /** 승인됨 */
    APPROVED,
    /** 반려됨 */
    REJECTED,
    /** 승인된 휴가에 대해 취소 요청됨(결재자 결재 대기) */
    CANCEL_REQUESTED,
    /** 취소됨 */
    CANCELLED
}
