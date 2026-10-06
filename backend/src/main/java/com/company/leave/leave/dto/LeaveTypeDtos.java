package com.company.leave.leave.dto;

import com.company.leave.leave.domain.AnnualDeductionMode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.domain.SpecialLeaveRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class LeaveTypeDtos {

    private LeaveTypeDtos() {
    }

    /**
     * @param portion                  휴가 단위(FULL 종일·HALF 반차·QUARTER 반반차·HOURLY 시간차)
     * @param halfDay                  portion == HALF (기존 화면 호환용)
     * @param annualDeductionMode      연차 차감 방식(DEDUCT 연차처럼 차감·EXHAUST_FIRST 연차 먼저 소진·NONE 연차와 무관)
     * @param deductFromAnnual         annualDeductionMode == DEDUCT (기존 화면 호환용)
     * @param requiresAnnualExhausted  annualDeductionMode == EXHAUST_FIRST (신청 화면의 병가·공가 안내용)
     * @param policyEnabled            현재 정책에서 이 단위를 쓸 수 있는지(반차·반반차·시간차 켜기/끄기). 신청 화면 목록 필터용
     * @param specialRules             이 종류에 연결된 경조사 규정. 비어 있지 않으면 신청 때 하나를 골라야 한다
     */
    public record Response(
            Long id,
            String code,
            String name,
            BigDecimal deductDays,
            boolean paid,
            DayPortion portion,
            boolean halfDay,
            AnnualDeductionMode annualDeductionMode,
            boolean deductFromAnnual,
            boolean requiresAnnualExhausted,
            String colorHex,
            int sortOrder,
            boolean active,
            boolean policyEnabled,
            List<SpecialRuleOption> specialRules) {

        public static Response from(LeaveType t, LeavePolicy policy, List<SpecialLeaveRule> rules) {
            return new Response(t.getId(), t.getCode(), t.getName(), t.getDeductDays(),
                    t.isPaid(), t.getPortion(), t.isHalfDay(), t.getAnnualDeductionMode(), t.isDeductFromAnnual(),
                    t.isRequiresAnnualExhausted(), t.getColorHex(), t.getSortOrder(), t.isActive(),
                    policy.allows(t.getPortion()),
                    rules.stream().map(r -> new SpecialRuleOption(r.getId(), r.getName(), r.getDays())).toList());
        }
    }

    /** 신청 화면 선택지용 경조사 규정. days 는 근무일 기준 최대 일수. */
    public record SpecialRuleOption(Long id, String name, BigDecimal days) {
    }

    /** portion 이 없으면 종일(FULL). */
    public record Create(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 60) String name,
            @NotNull BigDecimal deductDays,
            boolean paid,
            DayPortion portion,
            @NotNull AnnualDeductionMode annualDeductionMode,
            @NotBlank @Size(max = 7) String colorHex,
            Integer sortOrder) {
    }

    /** portion 이 없으면 종일(FULL). */
    public record Update(
            @NotBlank @Size(max = 60) String name,
            @NotNull BigDecimal deductDays,
            boolean paid,
            DayPortion portion,
            @NotNull AnnualDeductionMode annualDeductionMode,
            @NotBlank @Size(max = 7) String colorHex,
            Integer sortOrder,
            boolean active) {
    }
}
