package com.company.leave.leave.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import com.company.leave.employee.domain.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 휴가 신청/결재 건.
 */
@Entity
@Table(name = "leave_requests")
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 기록/표시용 기간(근무일, 반차=0.5). 캘린더·리포트에 사용. */
    @Column(nullable = false)
    private BigDecimal days;

    /** 실제 연차 잔액 차감액(= 근무일수 × 휴가유형 deductDays, 반차=deductDays). 비차감 유형은 0. */
    @Column(name = "deducted_days", nullable = false)
    private BigDecimal deductedDays = BigDecimal.ZERO;

    @Column(name = "applied_year", nullable = false)
    private int appliedYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    protected LeaveRequest() {
    }

    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate,
                        LocalDate endDate, BigDecimal days, BigDecimal deductedDays,
                        int appliedYear, String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.deductedDays = deductedDays != null ? deductedDays : BigDecimal.ZERO;
        this.appliedYear = appliedYear;
        this.reason = reason;
        this.status = LeaveRequestStatus.PENDING;
    }

    public void approve(Employee approver, Instant when) {
        this.status = LeaveRequestStatus.APPROVED;
        this.approver = approver;
        this.approvedAt = when;
    }

    public void reject(Employee approver, String reason, Instant when) {
        this.status = LeaveRequestStatus.REJECTED;
        this.approver = approver;
        this.rejectReason = reason;
        this.approvedAt = when;
    }

    public void cancel() {
        this.status = LeaveRequestStatus.CANCELLED;
    }

    /** 공휴일 추가 등으로 일수를 다시 계산한 값으로 덮어쓴다(차감 전·후 모두 새 값 기준). */
    public void adjustDays(BigDecimal days, BigDecimal deductedDays) {
        this.days = days;
        this.deductedDays = deductedDays;
    }

    /** 시스템 자동 취소(예: 기간 전체가 공휴일이 됨). 사유를 남긴다. */
    public void autoCancel(String reason) {
        this.status = LeaveRequestStatus.CANCELLED;
        this.cancelReason = reason;
    }

    /** 승인된 휴가에 대한 취소 요청 (팀장 재승인 대기). */
    public void requestCancel(String reason) {
        this.status = LeaveRequestStatus.CANCEL_REQUESTED;
        this.cancelReason = reason;
    }

    /** 취소 요청 반려 → 승인 상태로 복귀. */
    public void rejectCancel() {
        this.status = LeaveRequestStatus.APPROVED;
        this.cancelReason = null;
    }

    public boolean isPending() {
        return status == LeaveRequestStatus.PENDING;
    }

    public boolean isCancelRequested() {
        return status == LeaveRequestStatus.CANCEL_REQUESTED;
    }

    public boolean isApproved() {
        return status == LeaveRequestStatus.APPROVED;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getDays() {
        return days;
    }

    public BigDecimal getDeductedDays() {
        return deductedDays;
    }

    public int getAppliedYear() {
        return appliedYear;
    }

    public LeaveRequestStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Employee getApprover() {
        return approver;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
