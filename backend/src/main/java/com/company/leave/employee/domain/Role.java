package com.company.leave.employee.domain;

/**
 * 사용자 권한(역할). Spring Security 에서는 {@code ROLE_} 접두사를 붙여 사용한다.
 * 역할 간 상속은 없다: 상위 역할이 하위 역할의 권한을 자동으로 갖지 않으며, 필요한 역할을 각각 부여한다.
 */
public enum Role {
    /**
     * 시스템 관리자 — 관리 전용 계정(admin, system_account)에만 부여. 일반 직원에게는 줄 수 없다.
     * 관리 기능 전부와, 휴가 결재는 인사관리자와 같은 권한(회사 합의): 모든 신청 결재·강제 취소·강제 등록.
     * 직원이 아니므로 본인 휴가 신청은 없다.
     */
    SYSTEM_ADMIN("시스템관리자"),
    /**
     * 인사 관리자 — 사용자·부서·정책 운영, 모든 휴가 신청 결재(본인 신청 자가 승인 포함),
     * 승인된 휴가의 강제 취소(시작 후 포함, 사유 필수), 다른 직원 휴가의 강제 등록.
     */
    HR_ADMIN("인사관리자"),
    /**
     * 팀장 — 맡은 부서(하위 부서 포함) 직원의 휴가 결재(하위 부서 팀장 포함), 취소 요청 결재, 팀 일정 등록.
     * 본인 휴가는 상위 부서 팀장이 결재하고, 최상위 부서 팀장은 자가 승인한다.
     */
    TEAM_LEAD("팀장"),
    /** 사원 — 휴가 신청·조회·취소 요청 */
    EMPLOYEE("사원");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** 화면 표시 이름(목록 검색에도 쓴다). */
    public String label() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
