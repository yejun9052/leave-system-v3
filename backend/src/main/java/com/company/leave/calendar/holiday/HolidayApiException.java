package com.company.leave.calendar.holiday;

/** 공휴일 API 호출·응답 해석 실패. */
public class HolidayApiException extends RuntimeException {

    public HolidayApiException(String message) {
        super(message);
    }

    public HolidayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
