package com.leavesystem.leave.domain.leave;

/**
 * 연차 원장 유형. 설계 문서 5.1 원장 중심 계산.
 *
 * <p>잔여 연차는 사원별 원장 {@code days} 합계이며, 별도의 잔여 컬럼을 두지 않는다.
 */
public enum LeaveHistoryType {

    /** 부여: 양수. */
    GRANT,

    /** 사용: 음수. 관리자 최종 승인 시점에만 기록한다. */
    USE,

    /** 취소 복구: 양수. 승인 건의 취소 승인 시 기록한다. */
    CANCEL,

    /** 관리자 정정: 양수·음수 모두 가능. */
    ADJUST,

    /** 기산일 소멸: 음수. */
    EXPIRE
}
