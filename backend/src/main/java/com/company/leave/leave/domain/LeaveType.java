package com.company.leave.leave.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 휴가 종류 (연차, 오전/오후 반차, 경조사, 병가 등).
 */
@Entity
@Table(name = "leave_types")
@Getter
public class LeaveType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** 차감 일수. 종일=하루당, 반차·반반차=1회당, 시간차=1시간당. 0=비차감 */
    @Column(name = "deduct_days", nullable = false)
    private BigDecimal deductDays = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean paid = true;

    /** 휴가 단위(종일·반차·반반차·시간차). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayPortion portion = DayPortion.FULL;

    /** 연차 차감 방식(연차처럼 차감 / 연차 먼저 소진 / 연차와 무관). 연차 관련 판단은 이 값만 본다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "annual_deduction_mode", nullable = false, length = 20)
    private AnnualDeductionMode annualDeductionMode = AnnualDeductionMode.DEDUCT;

    // 예전 두 스위치 열. 읽지 않고 annualDeductionMode 에 맞춰 같이 저장만 한다(되돌릴 때를 위해 DB 에 남김)
    @Column(name = "deduct_from_annual", nullable = false)
    private boolean deductFromAnnual = true;

    @Column(name = "requires_annual_exhausted", nullable = false)
    private boolean requiresAnnualExhausted = false;

    /** 연차 사용 금지 기간(블랙아웃)에도 신청 가능(경조사·공가). 차감 방식과 따로 정한다. */
    @Column(name = "allowed_during_blackout", nullable = false)
    private boolean allowedDuringBlackout = false;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#4f46e5";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected LeaveType() {
    }

    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid, DayPortion portion,
                     AnnualDeductionMode annualDeductionMode, String colorHex, int sortOrder) {
        this.code = code;
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.portion = portion != null ? portion : DayPortion.FULL;
        applyDeductionMode(annualDeductionMode);
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }

    /** 예전 두 스위치로 만드는 생성(기존 호출부 호환). {@link AnnualDeductionMode#of} 로 변환한다. */
    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid, DayPortion portion,
                     boolean deductFromAnnual, boolean requiresAnnualExhausted, String colorHex, int sortOrder) {
        this(code, name, deductDays, paid, portion,
                AnnualDeductionMode.of(deductFromAnnual, requiresAnnualExhausted), colorHex, sortOrder);
    }

    /** 종일/반차만 구분하는 간단 생성(기존 호출부 호환). */
    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid,
                     boolean halfDay, boolean deductFromAnnual, String colorHex, int sortOrder) {
        this(code, name, deductDays, paid, halfDay ? DayPortion.HALF : DayPortion.FULL,
                deductFromAnnual, false, colorHex, sortOrder);
    }

    public void update(String name, BigDecimal deductDays, boolean paid, DayPortion portion,
                       AnnualDeductionMode annualDeductionMode, String colorHex, int sortOrder, boolean active) {
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.portion = portion != null ? portion : DayPortion.FULL;
        applyDeductionMode(annualDeductionMode);
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** 연차 사용 금지 기간에도 신청 가능하게(또는 불가하게). */
    public LeaveType allowDuringBlackout(boolean allowed) {
        this.allowedDuringBlackout = allowed;
        return this;
    }

    private void applyDeductionMode(AnnualDeductionMode mode) {
        this.annualDeductionMode = mode != null ? mode : AnnualDeductionMode.DEDUCT;
        this.deductFromAnnual = annualDeductionMode == AnnualDeductionMode.DEDUCT;
        this.requiresAnnualExhausted = annualDeductionMode == AnnualDeductionMode.EXHAUST_FIRST;
    }

    public boolean isHalfDay() {
        return portion == DayPortion.HALF;
    }

    /** 반차·반반차·시간차(하루만, 1일 미만). */
    public boolean isPartialDay() {
        return portion.isPartial();
    }

    /** 연차처럼 차감(DEDUCT). */
    public boolean isDeductFromAnnual() {
        return annualDeductionMode == AnnualDeductionMode.DEDUCT;
    }

    /**
     * 회사 규정: 연차 먼저 소진(EXHAUST_FIRST). 승인 기준 잔여 1일 미만이고 대기 중인 연차 차감 신청이 없을 때만
     * 신청·승인되며, 승인 때 남은 연차(1일 미만)는 소멸된다.
     */
    public boolean isRequiresAnnualExhausted() {
        return annualDeductionMode == AnnualDeductionMode.EXHAUST_FIRST;
    }
}
