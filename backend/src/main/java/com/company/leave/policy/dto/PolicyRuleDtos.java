package com.company.leave.policy.dto;

import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class PolicyRuleDtos {

    private PolicyRuleDtos() {
    }

    // --- 장기근속 포상 ---
    public record AwardRule(Long id, int years, BigDecimal bonusDays, String name) {
        public static AwardRule from(ServiceAwardRule r) {
            return new AwardRule(r.getId(), r.getYears(), r.getBonusDays(), r.getName());
        }
    }

    public record AwardRuleRequest(
            @Min(1) int years,
            @NotNull BigDecimal bonusDays,
            String name) {
    }

    // --- 경조사 규칙 ---
    /** @param annualLimit 연간 사용 횟수(달력 연도). null 이면 제한 없음 */
    public record SpecialRule(Long id, String name, BigDecimal days, String leaveTypeCode, int sortOrder,
                              Integer annualLimit) {
        public static SpecialRule from(SpecialLeaveRule r) {
            return new SpecialRule(r.getId(), r.getName(), r.getDays(),
                    r.getLeaveTypeCode(), r.getSortOrder(), r.getAnnualLimit());
        }
    }

    /**
     * @param sortOrder   null 이면 추가는 0, 수정은 지금 순서 그대로
     * @param annualLimit 연간 사용 횟수(1 이상). null 이면 제한 없음
     */
    public record SpecialRuleRequest(
            @NotBlank String name,
            @NotNull BigDecimal days,
            String leaveTypeCode,
            Integer sortOrder,
            @Min(1) Integer annualLimit) {

        public SpecialRuleRequest(String name, BigDecimal days, String leaveTypeCode, Integer sortOrder) {
            this(name, days, leaveTypeCode, sortOrder, null);
        }
    }

    // --- 블랙아웃 ---
    public record Blackout(Long id, LocalDate startDate, LocalDate endDate, String name) {
        public static Blackout from(BlackoutPeriod b) {
            return new Blackout(b.getId(), b.getStartDate(), b.getEndDate(), b.getName());
        }
    }

    public record BlackoutRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank String name) {
    }
}
