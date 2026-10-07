package com.company.leave.policy.dto;

import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

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
            boolean hourlyEnabled,
            int maxConcurrentAbsence,
            int minAdvanceDays,
            int maxConsecutiveDays,
            boolean promotionEnabled,
            List<Integer> promotionMonths,
            boolean carryOverEnabled,
            BigDecimal maxCarryOverDays,
            boolean nextPeriodReservationEnabled,
            BlackoutConflictMode blackoutConflictMode) {

        public static Response from(LeavePolicy p) {
            return new Response(p.getId(), p.getGrantBasis(), p.getFiscalStartMonth(),
                    p.getFiscalStartDay(), p.getBaseAnnualDays(), p.getSeniorityStepYears(),
                    p.getSeniorityIncrementDays(), p.getMaxAnnualDays(), p.isMonthlyAccrualEnabled(),
                    p.getMonthlyAccrualMax(), p.isAllowNegative(), p.isHalfDayEnabled(),
                    p.isHourlyEnabled(),
                    p.getMaxConcurrentAbsence(), p.getMinAdvanceDays(), p.getMaxConsecutiveDays(),
                    p.isPromotionEnabled(), p.getPromotionMonths(), p.isCarryOverEnabled(), p.getMaxCarryOverDays(),
                    p.isNextPeriodReservationEnabled(), p.getBlackoutConflictMode());
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
            boolean hourlyEnabled,
            @Min(0) int maxConcurrentAbsence,
            @Min(0) int minAdvanceDays,
            @Min(0) int maxConsecutiveDays,
            boolean carryOverEnabled,
            @NotNull BigDecimal maxCarryOverDays,
            /** 다음 연차 기간 예약 허용. 보내지 않으면(이전 화면) 켜짐 */
            Boolean nextPeriodReservationEnabled,
            /** 금지 기간 등록 시 기존 휴가 처리 방식. 보내지 않으면 그대로 */
            BlackoutConflictMode blackoutConflictMode) {

        public UpdateRequest(GrantBasis grantBasis, int fiscalStartMonth, int fiscalStartDay,
                             BigDecimal baseAnnualDays, int seniorityStepYears, BigDecimal seniorityIncrementDays,
                             BigDecimal maxAnnualDays, boolean monthlyAccrualEnabled, int monthlyAccrualMax,
                             boolean allowNegative, boolean halfDayEnabled, boolean hourlyEnabled,
                             int maxConcurrentAbsence, int minAdvanceDays, int maxConsecutiveDays,
                             boolean carryOverEnabled, BigDecimal maxCarryOverDays,
                             Boolean nextPeriodReservationEnabled) {
            this(grantBasis, fiscalStartMonth, fiscalStartDay, baseAnnualDays, seniorityStepYears,
                    seniorityIncrementDays, maxAnnualDays, monthlyAccrualEnabled, monthlyAccrualMax, allowNegative,
                    halfDayEnabled, hourlyEnabled, maxConcurrentAbsence, minAdvanceDays, maxConsecutiveDays,
                    carryOverEnabled, maxCarryOverDays, nextPeriodReservationEnabled, null);
        }

        public LeavePolicy.Settings toSettings() {
            return new LeavePolicy.Settings(grantBasis, fiscalStartMonth, fiscalStartDay,
                    baseAnnualDays, seniorityStepYears, seniorityIncrementDays, maxAnnualDays,
                    monthlyAccrualEnabled, monthlyAccrualMax, allowNegative, halfDayEnabled,
                    hourlyEnabled,
                    maxConcurrentAbsence, minAdvanceDays, maxConsecutiveDays,
                    carryOverEnabled, maxCarryOverDays,
                    !Boolean.FALSE.equals(nextPeriodReservationEnabled));
        }
    }
}
