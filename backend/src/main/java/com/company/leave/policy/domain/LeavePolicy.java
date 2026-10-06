package com.company.leave.policy.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import com.company.leave.leave.domain.DayPortion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

/**
 * 전사 연차 운영 정책 (단일 활성 레코드).
 */
@Entity
@Table(name = "leave_policy")
@Getter
public class LeavePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 부여 기준 ---
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_basis", nullable = false, length = 20)
    private GrantBasis grantBasis = GrantBasis.HIRE_DATE;

    @Column(name = "fiscal_start_month", nullable = false)
    private int fiscalStartMonth = 1;

    @Column(name = "fiscal_start_day", nullable = false)
    private int fiscalStartDay = 1;

    // --- 근속 가산 (근로기준법 기본값) ---
    @Column(name = "base_annual_days", nullable = false)
    private BigDecimal baseAnnualDays = BigDecimal.valueOf(15);

    @Column(name = "seniority_step_years", nullable = false)
    private int seniorityStepYears = 2;

    @Column(name = "seniority_increment_days", nullable = false)
    private BigDecimal seniorityIncrementDays = BigDecimal.ONE;

    @Column(name = "max_annual_days", nullable = false)
    private BigDecimal maxAnnualDays = BigDecimal.valueOf(25);

    @Column(name = "monthly_accrual_enabled", nullable = false)
    private boolean monthlyAccrualEnabled = true;

    @Column(name = "monthly_accrual_max", nullable = false)
    private int monthlyAccrualMax = 11;

    // --- 사용 규칙 ---
    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative = false;

    @Column(name = "half_day_enabled", nullable = false)
    private boolean halfDayEnabled = true;

    // lead_approval_required 열(예전 2단계 결재 스위치)은 단일 결재로 바뀌어 읽지 않는다. 열은 DB 에 남긴다(기본값 TRUE).

    /** 시간차(1시간 = 0.125일) 사용 여부. */
    @Column(name = "hourly_enabled", nullable = false)
    private boolean hourlyEnabled = false;

    /** 같은 부서 동시 부재 최대 인원 (0 = 무제한) */
    @Column(name = "max_concurrent_absence", nullable = false)
    private int maxConcurrentAbsence = 0;

    /** 최소 사전 신청 기한(일). 0 = 제한 없음 */
    @Column(name = "min_advance_days", nullable = false)
    private int minAdvanceDays = 0;

    /** 최대 연속 사용일 (0 = 무제한) */
    @Column(name = "max_consecutive_days", nullable = false)
    private int maxConsecutiveDays = 0;

    // --- 촉진 / 이월 ---
    /** 연차 촉진 자동 발송 ON/OFF. 자동화 탭에서 따로 저장한다({@link #applyAutomation}). */
    @Column(name = "promotion_enabled", nullable = false)
    private boolean promotionEnabled = false;

    /** 자동 발송 시기: 사용 기한 몇 개월 전(1~6, 쉼표 구분, 예 "6,2"). */
    @Column(name = "promotion_months", nullable = false, length = 20)
    private String promotionMonths = "6,2";

    @Column(name = "carry_over_enabled", nullable = false)
    private boolean carryOverEnabled = false;

    @Column(name = "max_carry_over_days", nullable = false)
    private BigDecimal maxCarryOverDays = BigDecimal.ZERO;

    /** 다음 연차 기간(다음 기산일 이후) 날짜의 연차 신청 허용. 끄면 지금 기간 안에서만 신청. */
    @Column(name = "next_period_reservation_enabled", nullable = false)
    private boolean nextPeriodReservationEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    protected LeavePolicy() {
    }

    public static LeavePolicy createDefault() {
        return new LeavePolicy();
    }

    /** 정책 설정 일괄 적용. */
    public void apply(Settings s) {
        this.grantBasis = s.grantBasis();
        this.fiscalStartMonth = s.fiscalStartMonth();
        this.fiscalStartDay = s.fiscalStartDay();
        this.baseAnnualDays = s.baseAnnualDays();
        this.seniorityStepYears = s.seniorityStepYears();
        this.seniorityIncrementDays = s.seniorityIncrementDays();
        this.maxAnnualDays = s.maxAnnualDays();
        this.monthlyAccrualEnabled = s.monthlyAccrualEnabled();
        this.monthlyAccrualMax = s.monthlyAccrualMax();
        this.allowNegative = s.allowNegative();
        this.halfDayEnabled = s.halfDayEnabled();
        this.hourlyEnabled = s.hourlyEnabled();
        this.maxConcurrentAbsence = s.maxConcurrentAbsence();
        this.minAdvanceDays = s.minAdvanceDays();
        this.maxConsecutiveDays = s.maxConsecutiveDays();
        this.carryOverEnabled = s.carryOverEnabled();
        this.maxCarryOverDays = s.maxCarryOverDays() != null ? s.maxCarryOverDays() : BigDecimal.ZERO;
        this.nextPeriodReservationEnabled = s.nextPeriodReservationEnabled();
    }

    /** 정책 설정 값 캐리어. */
    public record Settings(
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
            boolean carryOverEnabled,
            BigDecimal maxCarryOverDays,
            boolean nextPeriodReservationEnabled) {
    }

    /** 해당 단위의 휴가를 현재 정책에서 신청할 수 있는지. 종일은 항상 가능. */
    public boolean allows(DayPortion portion) {
        return switch (portion) {
            case FULL -> true;
            case HALF -> halfDayEnabled;
            case HOURLY -> hourlyEnabled;
        };
    }

    /** 자동 발송 시기(사용 기한 몇 개월 전), 큰 값부터. */
    public List<Integer> getPromotionMonths() {
        return Arrays.stream(promotionMonths.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .map(Integer::valueOf).sorted(Comparator.reverseOrder()).toList();
    }

    /**
     * 자동화 설정 저장. 시기는 1~6개월, 겹치지 않게 정리한다. 켤 때는 시기가 하나 이상 있어야 한다.
     *
     * @throws IllegalArgumentException 시기가 범위를 벗어나거나 켰는데 시기가 없을 때
     */
    public void applyAutomation(boolean promotionEnabled, Collection<Integer> months) {
        List<Integer> sorted = (months == null ? List.<Integer>of() : months).stream()
                .distinct().sorted(Comparator.reverseOrder()).toList();
        if (sorted.stream().anyMatch(m -> m == null || m < 1 || m > 6)) {
            throw new IllegalArgumentException("발송 시기는 사용 기한 1~6개월 전 중에서 고를 수 있습니다.");
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("발송 시기를 하나 이상 골라 주세요.");
        }
        this.promotionEnabled = promotionEnabled;
        this.promotionMonths = String.join(",", sorted.stream().map(String::valueOf).toList());
    }
}
