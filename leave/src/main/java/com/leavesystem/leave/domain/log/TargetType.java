package com.leavesystem.leave.domain.log;

/** 이벤트로그가 가리키는 대상 종류. 설계 문서 7장 {@code ACTION_LOG.target_type}. */
public enum TargetType {

    LEAVE_REQUEST,
    LEAVE_HISTORY,
    EMPLOYEE,
    DEPARTMENT,
    LEAVE_TYPE,
    HOLIDAY,
    POLICY,
    NOTICE,
    NOTIFICATION,
    BATCH
}
