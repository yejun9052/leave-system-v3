package com.company.leave.employee.domain;

/**
 * 재직 상태.
 */
public enum EmployeeStatus {
    /** 재직 */
    ACTIVE("재직"),
    /** 휴직 */
    ON_LEAVE("휴직"),
    /** 퇴사 */
    RESIGNED("퇴사");

    private final String label;

    EmployeeStatus(String label) {
        this.label = label;
    }

    /** 화면 표시 이름(목록 검색에도 쓴다). */
    public String label() {
        return label;
    }
}
