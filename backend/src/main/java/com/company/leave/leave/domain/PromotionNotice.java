package com.company.leave.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 연차 사용 촉진 안내 발송 이력 한 건(V26). 사용 기한 전에 남은 연차를 알린 통보 증빙.
 */
@Entity
@Table(name = "promotion_notices")
public class PromotionNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** 연차 기간(그 해에 시작한 기간). */
    @Column(name = "balance_year", nullable = false)
    private int balanceYear;

    /** 사용 기한. */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 보낼 때 남은 연차. */
    @Column(name = "remaining_days", nullable = false)
    private BigDecimal remainingDays;

    /** 보낼 때 사용 기한까지 남은 날(D-day). */
    @Column(name = "days_left", nullable = false)
    private int daysLeft;

    /** 메일을 보낸 주소. 없으면 앱 알림만 보냈다. */
    @Column(length = 255)
    private String email;

    /** 보낸 관리자. 자동 발송이면 null. */
    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    protected PromotionNotice() {
    }

    public PromotionNotice(Long employeeId, int balanceYear, LocalDate periodEnd, BigDecimal remainingDays,
                           int daysLeft, String email, Long sentBy) {
        this.employeeId = employeeId;
        this.balanceYear = balanceYear;
        this.periodEnd = periodEnd;
        this.remainingDays = remainingDays;
        this.daysLeft = daysLeft;
        this.email = email;
        this.sentBy = sentBy;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public int getBalanceYear() {
        return balanceYear;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getRemainingDays() {
        return remainingDays;
    }

    public int getDaysLeft() {
        return daysLeft;
    }

    public String getEmail() {
        return email;
    }

    public Long getSentBy() {
        return sentBy;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
