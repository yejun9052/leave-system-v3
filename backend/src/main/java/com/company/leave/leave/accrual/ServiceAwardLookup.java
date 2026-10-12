package com.company.leave.leave.accrual;

import java.math.BigDecimal;

/**
 * 장기근속 포상: 근속 N년이 되는 연차 기간에 더해 주는 일수. 관리자가 정책 화면에서 등록한 규칙을 찾는다.
 * 규칙이 없으면 0(기본 규칙은 두지 않는다).
 */
@FunctionalInterface
public interface ServiceAwardLookup {

    /** 포상 없음(계산만 하는 테스트 등). */
    ServiceAwardLookup NONE = completedYears -> BigDecimal.ZERO;

    /** 기간 시작일에 채운 근속 연수에 해당하는 포상 일수. 없으면 0. */
    BigDecimal bonusDays(int completedYears);
}
