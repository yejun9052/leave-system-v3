package com.company.leave.leave.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** 하루당 차감 일수. 1.0=연차, 0.5=반차, 0=비차감 */
    @Column(name = "deduct_days", nullable = false)
    private BigDecimal deductDays = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean paid = true;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay = false;

    /** 연차 잔액에서 차감할지 여부 (경조/병가 등은 false 가능) */
    @Column(name = "deduct_from_annual", nullable = false)
    private boolean deductFromAnnual = true;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#4f46e5";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected LeaveType() {
    }

    public LeaveType(String code, String name, BigDecimal deductDays, boolean paid,
                     boolean halfDay, boolean deductFromAnnual, String colorHex, int sortOrder) {
        this.code = code;
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.halfDay = halfDay;
        this.deductFromAnnual = deductFromAnnual;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }

    public void update(String name, BigDecimal deductDays, boolean paid, boolean halfDay,
                       boolean deductFromAnnual, String colorHex, int sortOrder, boolean active) {
        this.name = name;
        this.deductDays = deductDays;
        this.paid = paid;
        this.halfDay = halfDay;
        this.deductFromAnnual = deductFromAnnual;
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

    public boolean isHalfDay() {
        return halfDay;
    }

    public boolean isDeductFromAnnual() {
        return deductFromAnnual;
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
