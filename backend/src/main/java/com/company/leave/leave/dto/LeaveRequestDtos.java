package com.company.leave.leave.dto;

import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class LeaveRequestDtos {

    private LeaveRequestDtos() {
    }

    public record Create(
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason) {
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
            LeaveRequestStatus status,
            String reason,
            String approverName,
            Instant approvedAt,
            String rejectReason,
            String cancelReason,
            Instant createdAt) {

        public static Response from(LeaveRequest r) {
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
