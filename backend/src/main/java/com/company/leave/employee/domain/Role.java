package com.company.leave.employee.domain;

/**
 * 사용자 권한(역할). Spring Security 에서는 {@code ROLE_} 접두사를 붙여 사용한다.
 */
public enum Role {
    /** 시스템 관리자 — 전체 권한 */
    SUPER_ADMIN,
    /** 인사 관리자 — 사용자/부서/연차 운영, 대리승인 */
    HR_ADMIN,
    /** 팀장 — 소속 팀원 결재/팀 캘린더 */
    TEAM_LEAD,
    /** 사원 — 휴가 신청/조회 */
    EMPLOYEE;

    public String authority() {
        return "ROLE_" + name();
    }
}
