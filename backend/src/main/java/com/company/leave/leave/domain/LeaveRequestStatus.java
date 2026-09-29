package com.company.leave.leave.domain;

public enum LeaveRequestStatus {
    /** 결재 대기 */
    PENDING,
    /** 승인됨 */
    APPROVED,
    /** 반려됨 */
    REJECTED,
    /** 승인된 휴가에 대해 취소 요청됨(팀장 재승인 대기) */
    CANCEL_REQUESTED,
    /** 취소됨 */
    CANCELLED
}
