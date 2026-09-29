package com.company.leave.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 경조사 등 사유별 휴가 규칙 (관계/사유별 부여 일수).
 * 예) 본인 결혼 5일, 배우자 출산 10일, 부모상 5일.
 */
@Entity
@Table(name = "special_leave_rules")
public class SpecialLeaveRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private BigDecimal days;

    /** 연결되는 휴가 종류 코드 (예: CONDOLENCE) */
    @Column(name = "leave_type_code", length = 40)
    private String leaveTypeCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected SpecialLeaveRule() {
    }

    public SpecialLeaveRule(String name, BigDecimal days, String leaveTypeCode, int sortOrder) {
        this.name = name;
        this.days = days;
        this.leaveTypeCode = leaveTypeCode;
        this.sortOrder = sortOrder;
    }

    public void update(String name, BigDecimal days, String leaveTypeCode, int sortOrder) {
        this.name = name;
        this.days = days;
        this.leaveTypeCode = leaveTypeCode;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDays() {
        return days;
    }

    public String getLeaveTypeCode() {
        return leaveTypeCode;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
