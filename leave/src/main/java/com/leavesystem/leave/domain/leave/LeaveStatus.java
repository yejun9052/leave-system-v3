package com.leavesystem.leave.domain.leave;

/**
 * 휴가 신청 상태. 설계 문서 6.2 상태와 전이.
 *
 * <p>팀장 확인과 상위 부서장 확인을 상태로 나누지 않는다.
 * 상위 확인자는 {@link LeaveRequest#getEscalatedTo()} 로 구분한다.
 */
public enum LeaveStatus {

    /** 신청 후 대기. 본인 취소·1차 확인·반려 가능. */
    PENDING,

    /** 팀장 또는 상위 부서장 확인 완료. 관리자 승인·반려 대기. */
    LEADER_OK,

    /** 최종 승인. */
    APPROVED,

    /** 반려. */
    REJECTED,

    /** 승인 전 본인 취소. */
    CANCELED,

    /** 승인 건의 취소요청 대기. */
    CANCEL_REQUESTED,

    /** 승인 건의 취소 완료. */
    CANCELED_DONE
}
