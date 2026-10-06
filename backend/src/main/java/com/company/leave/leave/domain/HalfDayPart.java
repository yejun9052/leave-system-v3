package com.company.leave.leave.domain;

/**
 * 종일 단위 휴가 종류를 반차로 신청했을 때의 오전·오후(0.5일짜리 경조사 규정, 예: 생일 반차).
 * 오전 반차·오후 반차 종류는 종류 자체가 오전·오후를 나타내므로 쓰지 않는다.
 */
public enum HalfDayPart {
    AM("오전"),
    PM("오후");

    private final String label;

    HalfDayPart(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
