package com.company.leave.policy.dto;

import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record Response(
            Long id,
            GrantBasis grantBasis,
            int fiscalStartMonth,
            int fiscalStartDay,
            BigDecimal baseAnnualDays,
            int seniorityStepYears,
            BigDecimal seniorityIncrementDays,
            BigDecimal maxAnnualDays,
            boolean monthlyAccrualEnabled,
            int monthlyAccrualMax,
            boolean allowNegative,
            boolean halfDayEnabled,
            int maxConcurrentAbsence,
            int minAdvanceDays,
            int maxConsecutiveDays,
            boolean promotionEnabled,
            boolean carryOverEnabled,
            BigDecimal maxCarryOverDays) {

        public static Response from(LeavePolicy p) {
            return new Response(p.getId(), p.getGrantBasis(), p.getFiscalStartMonth(),
                    p.getFiscalStartDay(), p.getBaseAnnualDays(), p.getSeniorityStepYears(),
                    p.getSeniorityIncrementDays(), p.getMaxAnnualDays(), p.isMonthlyAccrualEnabled(),
                    p.getMonthlyAccrualMax(), p.isAllowNegative(), p.isHalfDayEnabled(),
                    p.getMaxConcurrentAbsence(), p.getMinAdvanceDays(), p.getMaxConsecutiveDays(),
                    p.isPromotionEnabled(), p.isCarryOverEnabled(), p.getMaxCarryOverDays());
        }
    }

    public record UpdateRequest(
            @NotNull GrantBasis grantBasis,
            @Min(1) @Max(12) int fiscalStartMonth,
            @Min(1) @Max(31) int fiscalStartDay,
            @NotNull BigDecimal baseAnnualDays,
            @Min(1) int seniorityStepYears,
            @NotNull BigDecimal seniorityIncrementDays,
            @NotNull BigDecimal maxAnnualDays,
            boolean monthlyAccrualEnabled,
            @Min(0) int monthlyAccrualMax,
            boolean allowNegative,
            boolean halfDayEnabled,
            @Min(0) int maxConcurrentAbsence,
            @Min(0) int minAdvanceDays,
            @Min(0) int maxConsecutiveDays,
            boolean promotionEnabled,
            boolean carryOverEnabled,
            @NotNull BigDecimal maxCarryOverDays) {

        public LeavePolicy.Settings toSettings() {
            return new LeavePolicy.Settings(grantBasis, fiscalStartMonth, fiscalStartDay,
                    baseAnnualDays, seniorityStepYears, seniorityIncrementDays, maxAnnualDays,
                    monthlyAccrualEnabled, monthlyAccrualMax, allowNegative, halfDayEnabled,
                    maxConcurrentAbsence, minAdvanceDays, maxConsecutiveDays,
                    promotionEnabled, carryOverEnabled, maxCarryOverDays);
        }
    }
}
