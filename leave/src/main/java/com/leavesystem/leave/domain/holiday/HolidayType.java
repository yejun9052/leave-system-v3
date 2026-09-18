package com.leavesystem.leave.domain.holiday;

/** 휴일·기간 유형. 설계 문서 7장 {@code HOLIDAY.type}. */
public enum HolidayType {

    /** 공휴일. */
    PUBLIC,

    /** 사내휴일. */
    COMPANY,

    /** 신청 금지 기간. 서버에서 신청 자체를 거부한다. */
    BLOCKED
}
