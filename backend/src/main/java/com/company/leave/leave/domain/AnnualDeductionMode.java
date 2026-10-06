package com.company.leave.leave.domain;

/**
 * 휴가 종류가 연차와 어떻게 얽히는지. 휴가 종류마다 하나를 고른다.
 * <ul>
 *   <li>DEDUCT: 연차처럼 차감. 쓴 일수만큼 연차에서 뺀다(연차·반차·시간차)</li>
 *   <li>EXHAUST_FIRST: 회사 규정 — 연차 먼저 소진. 사용 가능 연차가 1일 미만일 때만 신청할 수 있고,
 *       승인되면 남은 연차(1일 미만)가 소멸된다(병가·공가 기본값)</li>
 *   <li>NONE: 법정 기준 — 연차와 무관. 연차와 상관없이 신청하고 차감하지 않는다(경조사 기본값)</li>
 * </ul>
 */
public enum AnnualDeductionMode {
    DEDUCT,
    EXHAUST_FIRST,
    NONE;

    /** 예전 두 스위치(연차에서 차감 / 잔여 연차 소진 후 사용)에서 변환. 둘 다 켜져 있으면 차감이 우선(V2 마이그레이션과 같은 규칙). */
    public static AnnualDeductionMode of(boolean deductFromAnnual, boolean requiresAnnualExhausted) {
        if (deductFromAnnual) {
            return DEDUCT;
        }
        return requiresAnnualExhausted ? EXHAUST_FIRST : NONE;
    }
}
