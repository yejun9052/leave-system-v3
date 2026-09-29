package com.company.leave.policy.domain;

/**
 * 연차 부여 기준.
 */
public enum GrantBasis {
    /** 입사일 기준 (근로기준법 원칙) */
    HIRE_DATE,
    /** 회계연도 기준 (매년 회계 시작일 일괄 부여) */
    FISCAL_YEAR
}
