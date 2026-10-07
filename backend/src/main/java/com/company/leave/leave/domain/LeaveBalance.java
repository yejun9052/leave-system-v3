package com.company.leave.leave.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 사용자의 연도별 연차 잔액.
 * remaining = granted + carried_over - used - expired
 */
@Entity
@Table(name = "leave_balances")
@Getter
public class LeaveBalance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private BigDecimal granted = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(name = "carried_over", nullable = false)
    private BigDecimal carriedOver = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal expired = BigDecimal.ZERO;

    /** 낙관적 락: 동시 승인/차감 시 갱신 유실(lost update) 방지. */
    @Getter(AccessLevel.NONE)
    @Version
    @Column(nullable = false)
    private long version;

    protected LeaveBalance() {
    }

    public LeaveBalance(Long employeeId, int year) {
        this.employeeId = employeeId;
        this.year = year;
    }

    public BigDecimal remaining() {
        return granted.add(carriedOver).subtract(used).subtract(expired);
    }

    /** 부여 일수를 설정(재계산 시 덮어씀). */
    public void setGranted(BigDecimal granted) {
        this.granted = granted;
    }

    public void addUsed(BigDecimal days) {
        this.used = this.used.add(days);
    }

    public void restoreUsed(BigDecimal days) {
        this.used = this.used.subtract(days);
        if (this.used.signum() < 0) {
            this.used = BigDecimal.ZERO;
        }
    }

    public void setCarriedOver(BigDecimal carriedOver) {
        this.carriedOver = carriedOver;
    }

    public void setExpired(BigDecimal expired) {
        this.expired = expired;
    }

    /** 남은 연차 소멸(병가·공가 승인 시). */
    public void forfeit(BigDecimal days) {
        this.expired = this.expired.add(days);
    }

    /** 소멸 되돌림(소멸을 일으킨 병가·공가가 취소될 때). */
    public void restoreForfeit(BigDecimal days) {
        this.expired = this.expired.subtract(days);
        if (this.expired.signum() < 0) {
            this.expired = BigDecimal.ZERO;
        }
    }
}
