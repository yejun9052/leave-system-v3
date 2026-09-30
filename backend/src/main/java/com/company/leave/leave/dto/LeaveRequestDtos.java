package com.company.leave.leave.dto;

import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class LeaveRequestDtos {

    private LeaveRequestDtos() {
    }

    /**
     * @param hours               시간차의 시간 수(1~3). 다른 종류는 무시
     * @param forfeitAcknowledged 병가·공가 승인 시 남은 연차가 소멸된다는 안내를 확인했는지(소멸분이 있을 때 필수)
     * @param specialRuleId       경조사 규정(규정이 연결된 종류면 필수, 신청 근무일 수 ≤ 규정 일수)
     */
    public record Create(
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason,
            @Min(1) @Max(DayPortion.MAX_HOURLY_HOURS) Integer hours,
            Boolean forfeitAcknowledged,
            Long specialRuleId) {

        public Create(Long leaveTypeId, LocalDate startDate, LocalDate endDate, String reason) {
            this(leaveTypeId, startDate, endDate, reason, null, null, null);
        }

        public Create(Long leaveTypeId, LocalDate startDate, LocalDate endDate, String reason,
                      Integer hours, Boolean forfeitAcknowledged) {
            this(leaveTypeId, startDate, endDate, reason, hours, forfeitAcknowledged, null);
        }
    }

    /**
     * 휴가 종류별 신청 가능 여부(신청 화면 안내용).
     *
     * @param allowed       지금 신청할 수 있는지
     * @param reason        불가 사유(allowed=false 일 때)
     * @param remainingDays 승인 기준 잔여 연차
     * @param forfeitDays   승인 시 소멸될 남은 연차(0 이면 경고 불필요)
     */
    public record Eligibility(boolean allowed, String reason, BigDecimal remainingDays, BigDecimal forfeitDays) {
    }

    public record Reject(@Size(max = 500) String reason) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    public record Response(
            Long id,
            Long employeeId,
            String employeeName,
            String departmentName,
            Long leaveTypeId,
            String leaveTypeName,
            String leaveTypeColor,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            DayPortion portion,
            Integer hours,
            BigDecimal forfeitedDays,
            String specialRuleName,
            BigDecimal specialRuleDays,
            LeaveRequestStatus status,
            String reason,
            String approverName,
            Instant approvedAt,
            String rejectReason,
            String cancelReason,
            Instant createdAt) {

        public static Response from(LeaveRequest r) {
            DayPortion portion = r.getLeaveType().getPortion();
            return new Response(
                    r.getId(),
                    r.getEmployee().getId(),
                    r.getEmployee().getName(),
                    r.getEmployee().getDepartment() != null
                            ? r.getEmployee().getDepartment().getName() : null,
                    r.getLeaveType().getId(),
                    r.getLeaveType().getName(),
                    r.getLeaveType().getColorHex(),
                    r.getStartDate(),
                    r.getEndDate(),
                    r.getDays(),
                    portion,
                    portion == DayPortion.HOURLY ? WorkdayCalculator.hoursOf(r.getDays()) : null,
                    r.getForfeitedDays(),
                    r.getSpecialRuleName(),
                    r.getSpecialRuleDays(),
                    r.getStatus(),
                    r.getReason(),
                    r.getApprover() != null ? r.getApprover().getName() : null,
                    r.getApprovedAt(),
                    r.getRejectReason(),
                    r.getCancelReason(),
                    r.getCreatedAt());
        }
    }
}
