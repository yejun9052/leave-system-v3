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

/**
 * 휴가 종류 (연차, 오전/오후 반차, 경조사, 병가 등).
 */
@Entity
@Table(name = "leave_types")
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

    /** 연차 잔액에서 차감할지 여부 (경조/병가 등은 false 가능) */
    @Column(name = "deduct_from_annual", nullable = false)
    private boolean deductFromAnnual = true;

    /**
     * 잔여 연차를 먼저 소진해야 쓸 수 있는 종류(병가·공가). 승인 기준 잔여 1일 미만이고 대기 중인
     * 연차 차감 신청이 없을 때만 신청·승인되며, 승인 때 남은 연차(1일 미만)는 소멸된다.
     */
    @Column(name = "requires_annual_exhausted", nullable = false)
    private boolean requiresAnnualExhausted = false;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#4f46e5";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected LeaveType() {
    }

    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid, DayPortion portion,
                     boolean deductFromAnnual, boolean requiresAnnualExhausted, String colorHex, int sortOrder) {
        this.code = code;
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.portion = portion != null ? portion : DayPortion.FULL;
        this.deductFromAnnual = deductFromAnnual;
        this.requiresAnnualExhausted = requiresAnnualExhausted;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }

    /** 종일/반차만 구분하는 간단 생성(기존 호출부 호환). */
    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid,
                     boolean halfDay, boolean deductFromAnnual, String colorHex, int sortOrder) {
        this(code, name, deductDays, paid, halfDay ? DayPortion.HALF : DayPortion.FULL,
                deductFromAnnual, false, colorHex, sortOrder);
    }

    public void update(String name, BigDecimal deductDays, boolean paid, DayPortion portion,
                       boolean deductFromAnnual, boolean requiresAnnualExhausted,
                       String colorHex, int sortOrder, boolean active) {
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.portion = portion != null ? portion : DayPortion.FULL;
        this.deductFromAnnual = deductFromAnnual;
        this.requiresAnnualExhausted = requiresAnnualExhausted;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDeductDays() {
        return deductDays;
    }

    public boolean isPaid() {
        return paid;
    }

    public DayPortion getPortion() {
        return portion;
    }

    public boolean isHalfDay() {
        return portion == DayPortion.HALF;
    }

    /** 반차·반반차·시간차(하루만, 1일 미만). */
    public boolean isPartialDay() {
        return portion.isPartial();
    }

    public boolean isDeductFromAnnual() {
        return deductFromAnnual;
    }

    public boolean isRequiresAnnualExhausted() {
        return requiresAnnualExhausted;
    }

    public String getColorHex() {
        return colorHex;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
