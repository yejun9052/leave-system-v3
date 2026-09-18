package com.leavesystem.leave.domain.leave;

/** 연차 원장 행의 출처. 설계 문서 7장 {@code LEAVE_HISTORY.source}. */
public enum LeaveHistorySource {

    /** 시스템 자동 처리(부여·소멸·승인 차감 등). */
    SYSTEM,

    /** 관리자의 수기 정정. */
    ADMIN,

    /** 기존 엑셀 이관. */
    EXCEL_IMPORT
}
