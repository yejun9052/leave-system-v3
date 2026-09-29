package com.company.leave.calendar.domain;

public enum CalendarEventSource {
    /** 승인된 휴가에서 자동 생성 */
    LEAVE_REQUEST,
    /** 관리자/팀장이 직접 등록 */
    ADMIN_EVENT,
    /** 공휴일 */
    HOLIDAY
}
