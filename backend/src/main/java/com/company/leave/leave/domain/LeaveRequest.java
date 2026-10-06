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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 휴가 신청/결재 건.
 */
@Entity
@Table(name = "leave_requests")
@Getter
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

    /** 이 신청(병가·공가) 승인으로 소멸시킨 남은 연차. 취소되면 되돌린다. */
    @Column(name = "forfeited_days", nullable = false)
    private BigDecimal forfeitedDays = BigDecimal.ZERO;

    /** 신청 때 고른 경조사 규정(규정이 삭제되면 null, 이름·일수는 신청 당시 값으로 남음). */
    @Column(name = "special_rule_id")
    private Long specialRuleId;

    @Column(name = "special_rule_name", length = 60)
    private String specialRuleName;

    @Column(name = "special_rule_days")
    private BigDecimal specialRuleDays;

    /** 종일 단위 종류를 반차로 신청한 경우의 오전·오후(0.5일 경조사 규정). 그 외 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_part", length = 2)
    private HalfDayPart halfDayPart;

    @Column(name = "applied_year", nullable = false)
    private int appliedYear;

    /**
     * deductedDays 중 다음 연차 기간(applied_year + 1)에서 빼는 몫. 휴가가 기산일을 걸치면 기산일부터의 날짜분.
     * 나머지(deductedDays − 이 값)는 applied_year 기간에서 뺀다.
     */
    @Column(name = "next_period_deducted_days", nullable = false)
    private BigDecimal nextPeriodDeductedDays = BigDecimal.ZERO;

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

    // lead_approver_id·lead_approved_at·hr_direct_reason 열은 예전 2단계 결재 기록으로 DB 에 남아 있지만
    // 단일 결재에서는 쓰지 않아 매핑하지 않는다.

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 낙관적 락: 두 결재자가 같은 신청을 동시에 처리하면 한쪽만 반영된다. */
    @Getter(AccessLevel.NONE)
    @Version
    @Column(nullable = false)
    private long version;

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

    /** 연차 기간 배정: 시작일이 속한 기간과, 그중 다음 기간에서 뺄 몫(공휴일 재계산·기간 전환 때 다시 정함). */
    public void assignPeriods(int appliedYear, BigDecimal nextPeriodDeductedDays) {
        this.appliedYear = appliedYear;
        this.nextPeriodDeductedDays = nextPeriodDeductedDays != null ? nextPeriodDeductedDays : BigDecimal.ZERO;
    }

    /** 경조사 규정 연결(신청 당시 이름·일수를 함께 기록). */
    public void attachSpecialRule(Long ruleId, String ruleName, BigDecimal ruleDays) {
        this.specialRuleId = ruleId;
        this.specialRuleName = ruleName;
        this.specialRuleDays = ruleDays;
    }

    /** 종일 단위 종류를 오전·오후 반차로 신청(0.5일 경조사 규정). */
    public void markHalfDay(HalfDayPart part) {
        this.halfDayPart = part;
    }

    /** 이 신청의 실제 단위: 종일 종류를 반차로 신청했으면 HALF, 아니면 휴가 종류의 단위. */
    public DayPortion getPortion() {
        return halfDayPart != null ? DayPortion.HALF : leaveType.getPortion();
    }

    /** 반차·시간차(하루만, 1일 미만). 종일 종류의 반차 신청 포함. */
    public boolean isPartialDay() {
        return getPortion().isPartial();
    }

    /** 승인 때 소멸시킨 남은 연차를 기록한다. */
    public void recordForfeit(BigDecimal days) {
        this.forfeitedDays = days;
    }

    /** 소멸분을 되돌린 뒤 기록을 지운다(이중 복구 방지). 되돌릴 양을 반환. */
    public BigDecimal takeForfeitForRestore() {
        BigDecimal days = this.forfeitedDays;
        this.forfeitedDays = BigDecimal.ZERO;
        return days;
    }

    /** 시스템 자동 취소(예: 기간 전체가 공휴일이 됨). 사유를 남긴다. */
    public void autoCancel(String reason) {
        this.status = LeaveRequestStatus.CANCELLED;
        this.cancelReason = reason;
    }

    /** 인사관리자의 강제 취소(승인된 휴가, 시작 후도 가능). 사유를 남긴다. */
    public void forceCancel(String reason) {
        this.status = LeaveRequestStatus.CANCELLED;
        this.cancelReason = reason;
    }

    /** 승인된 휴가에 대한 취소 요청 (결재자 결재 대기). */
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

    /** 아직 결재 전(대기). 잔액·겹침 계산에서는 대기로 본다. */
    public boolean isAwaitingApproval() {
        return status == LeaveRequestStatus.PENDING;
    }

    public boolean isCancelRequested() {
        return status == LeaveRequestStatus.CANCEL_REQUESTED;
    }

    public boolean isApproved() {
        return status == LeaveRequestStatus.APPROVED;
    }

    /** applied_year 기간에서 빼는 몫. */
    public BigDecimal getCurrentPeriodDeductedDays() {
        return deductedDays.subtract(nextPeriodDeductedDays);
    }
}
