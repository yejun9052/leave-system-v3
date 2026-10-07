package com.company.leave.policy.dto;

import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

    /** 금지 기간 처리 결과: 자동 반려 / 자동 취소 / 유지 */
    public enum BlackoutAction { REJECT, CANCEL, KEEP }

    /** 금지 기간 등록·수정 전 미리보기의 휴가 한 건. */
    public record BlackoutImpactItem(Long requestId, String employeeName, String departmentName, String leaveTypeName,
                                     LocalDate startDate, LocalDate endDate, BigDecimal days, LeaveRequestStatus status,
                                     BlackoutAction action) {
        static BlackoutImpactItem of(LeaveRequest r, BlackoutAction action) {
            return new BlackoutImpactItem(r.getId(), r.getEmployee().getName(),
                    r.getEmployee().getDepartment() != null ? r.getEmployee().getDepartment().getName() : null,
                    r.getLeaveType().getName(), r.getStartDate(), r.getEndDate(), r.getDays(), r.getStatus(), action);
        }
    }

    /**
     * 금지 기간 등록·수정 전 미리보기. 저장하면 affected 를 처리하고, kept 는 그대로 둔다.
     *
     * @param mode     지금 정책의 처리 방식
     * @param extended 기간을 늘려 수정하는 경우(원래 기간에 남아 있는 휴가 경고를 띄운다)
     * @param affected 저장하면 자동 반려·자동 취소할 휴가(시작일 순)
     * @param kept     금지 기간과 겹치지만 그대로 두는 휴가
     */
    public record BlackoutImpact(BlackoutConflictMode mode, boolean extended, List<BlackoutImpactItem> affected,
                                 List<BlackoutImpactItem> kept) {
        public static BlackoutImpact of(BlackoutConflictMode mode, boolean extended,
                                        LeaveRequestService.BlackoutPlan plan) {
            List<BlackoutImpactItem> affected = Stream.concat(
                            plan.reject().stream().map(r -> BlackoutImpactItem.of(r, BlackoutAction.REJECT)),
                            plan.cancel().stream().map(r -> BlackoutImpactItem.of(r, BlackoutAction.CANCEL)))
                    .sorted(Comparator.comparing(BlackoutImpactItem::startDate)
                            .thenComparing(BlackoutImpactItem::employeeName))
                    .toList();
            List<BlackoutImpactItem> kept = plan.kept().stream()
                    .map(r -> BlackoutImpactItem.of(r, BlackoutAction.KEEP)).toList();
            return new BlackoutImpact(mode, extended, affected, kept);
        }
    }
}
