package com.company.leave.employee.domain;

/**
 * 사용자 권한(역할). 한 사람은 권한을 하나만 갖는다. Spring Security 에서는 {@code ROLE_} 접두사를 붙여 사용한다.
 * 상위 권한은 하위 권한의 기능을 모두 쓴다: 시스템 관리자 > 인사관리자 > 팀장 > 사원
 * (선언 순서가 높은 순, {@link #includes}, 보안 설정의 RoleHierarchy).
 */
public enum Role {
    /**
     * 시스템 관리자 — 관리 전용 계정(admin, system_account)에만 부여. 일반 직원에게는 줄 수 없다.
     * 관리 기능 전부와, 휴가 결재는 인사관리자와 같은 권한(회사 합의): 모든 신청 결재·강제 취소·강제 등록.
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

    /** 이 권한이 {@code other} 의 기능을 쓸 수 있는지(같거나 상위 권한). */
    public boolean includes(Role other) {
        return other != null && ordinal() <= other.ordinal();
    }

    /** 여러 권한 중 가장 높은 것 하나(없으면 사원). 예전 여러 권한 데이터·엑셀 양식을 하나로 줄일 때 쓴다. */
    public static Role highest(java.util.Collection<Role> roles) {
        return roles == null ? EMPLOYEE : roles.stream()
                .filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.naturalOrder())
                .orElse(EMPLOYEE);
    }

    public static Role highest(Role... roles) {
        return highest(java.util.Arrays.asList(roles));
    }

    /** Spring Security RoleHierarchy 표기: "ROLE_SYSTEM_ADMIN > ROLE_HR_ADMIN" 처럼 한 단계씩 줄로 잇는다. */
    public static String hierarchy() {
        Role[] all = values();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < all.length; i++) {
            sb.append(all[i].authority()).append(" > ").append(all[i + 1].authority()).append("\n");
        }
        return sb.toString();
    }
}
