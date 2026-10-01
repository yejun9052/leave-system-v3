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
     * 인사관리자 강제 등록: 다른 직원의 휴가를 바로 승인 상태로 등록한다.
     * 지난 날짜도 가능하지만 시작일은 근무일이어야 한다(주말·공휴일 불가). 사용 통제(블랙아웃·사전 신청 등)는 적용하지 않는다.
     */
    public record Register(
            @NotNull Long employeeId,
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason,
            @Min(1) @Max(DayPortion.MAX_HOURLY_HOURS) Integer hours,
            Long specialRuleId) {
    }

    /** 결재자 종류: 팀장 / 인사관리자 / 본인(최상위 부서 팀장·인사관리자의 자가 승인). */
    public enum ApproverKind { LEAD, HR, SELF }

    /**
     * 신청자의 결재 경로(신청 화면 안내용). 팀장·인사관리자·시스템 관리자 중 한 명이 승인하면 확정된다.
     *
     * @param approverKind 주 결재자: 상위 결재 팀장(LEAD), 결재 팀장이 없으면 인사관리자(HR), 자가 승인 가능하면 SELF
     * @param leadName     결재 팀장 이름(LEAD 일 때)
     */
    public record ApprovalRoute(ApproverKind approverKind, String leadName) {
    }

    /**
     * 휴가 종류별 신청 가능 여부(신청 화면 안내용).
     *
     * @param allowed       지금 신청할 수 있는지
     * @param reason        불가 사유(allowed=false 일 때)
     * @param remainingDays 승인 기준 잔여 연차
     * @param forfeitDays   승인 시 소멸될 남은 연차(0 이면 경고 불필요)
     * @param workdays      미리보기: 기간 근무일 수(주말·공휴일 제외). 미리보기가 아니거나 불가면 null
     * @param deduction     미리보기: 이번 신청의 연차 차감 예정액
     * @param pendingDays   미리보기: 결재 대기 중인 다른 신청의 차감 예정액 합계
     * @param remainingAfter 미리보기: 신청 후 잔여(잔여 − 결재 대기 − 이번 차감 − 소멸 예정)
     */
    public record Eligibility(boolean allowed, String reason, BigDecimal remainingDays, BigDecimal forfeitDays,
                              Integer workdays, BigDecimal deduction, BigDecimal pendingDays,
                              BigDecimal remainingAfter) {

        public Eligibility(boolean allowed, String reason, BigDecimal remainingDays, BigDecimal forfeitDays) {
            this(allowed, reason, remainingDays, forfeitDays, null, null, null, null);
        }
    }

    /**
     * 캘린더 날짜 상세의 휴가 한 건. 사유는 넣지 않는다(본인·결재자는 기존 화면에서 확인).
     *
     * @param hours 시간차만 시간 수, 그 외 null
     * @param mine  조회한 본인의 휴가인지
     */
    public record DayLeave(
            Long id,
            Long employeeId,
            String employeeName,
            Long departmentId,
            String departmentName,
            String leaveTypeName,
            String leaveTypeColor,
            DayPortion portion,
            Integer hours,
            LocalDate startDate,
            LocalDate endDate,
            LeaveRequestStatus status,
            boolean mine) {

        public static DayLeave from(LeaveRequest r, boolean mine) {
            DayPortion portion = r.getLeaveType().getPortion();
            return new DayLeave(
                    r.getId(),
                    r.getEmployee().getId(),
                    r.getEmployee().getName(),
                    r.getEmployee().getDepartmentId(),
                    r.getEmployee().getDepartment() != null ? r.getEmployee().getDepartment().getName() : null,
                    r.getLeaveType().getName(),
                    r.getLeaveType().getColorHex(),
                    portion,
                    portion == DayPortion.HOURLY ? WorkdayCalculator.hoursOf(r.getDays()) : null,
                    r.getStartDate(),
                    r.getEndDate(),
                    r.getStatus(),
                    mine);
        }
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
            Instant createdAt,
            /** 결재함에서만: 결재자 본인의 신청인지(자가 승인 건) */
            Boolean ownRequest,
            /** 결재함에서만: 결재자에게 보여 줄 경고(예: 경조사가 팀 동시 부재 한도 초과) */
            String approvalWarning,
            /** 신청 응답: 결재할 사람이 없는 경우의 안내 */
            String requestWarning) {

        public Response withInbox(boolean own, String warning) {
            return new Response(id, employeeId, employeeName, departmentName, leaveTypeId, leaveTypeName,
                    leaveTypeColor, startDate, endDate, days, portion, hours, forfeitedDays, specialRuleName,
                    specialRuleDays, status, reason, approverName, approvedAt, rejectReason, cancelReason,
                    createdAt, own, warning, requestWarning);
        }

        public Response withRequestWarning(String warning) {
            return new Response(id, employeeId, employeeName, departmentName, leaveTypeId, leaveTypeName,
                    leaveTypeColor, startDate, endDate, days, portion, hours, forfeitedDays, specialRuleName,
                    specialRuleDays, status, reason, approverName, approvedAt, rejectReason, cancelReason,
                    createdAt, ownRequest, approvalWarning, warning);
        }

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
                    r.getCreatedAt(),
                    null,
                    null,
                    null);
        }
    }
}
