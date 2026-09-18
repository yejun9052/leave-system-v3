package com.leavesystem.leave.domain.policy;

/**
 * 연차 부여 기준. 설계 문서 5.2.
 *
 * <p>값은 두 가지를 표현하되 MVP 로직은 {@link #HIRE_DATE} 만 구현한다.
 */
public enum GrantBasis {

    /** 입사일 기준. */
    HIRE_DATE,

    /** 회계연도 기준. 계산 로직은 이번 범위에서 제외한다. */
    FISCAL_YEAR
}
