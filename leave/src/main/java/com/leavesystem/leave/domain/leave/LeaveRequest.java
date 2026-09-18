package com.leavesystem.leave.domain.leave;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import com.leavesystem.leave.domain.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 휴가 신청. 설계 문서 6장, 7장.
 *
 * <p>신청 시점에 계산한 일수를 {@code days} 스냅샷으로 보관한다. 휴일이 나중에 바뀌어도
 * 승인된 신청의 일수는 유지하고 관리자 정정으로 처리한다(14.1).
 */
@Entity
@Getter
@Table(
        name = "leave_request",
        indexes = {
                @Index(name = "idx_leave_request__employee_id__start_date",
                        columnList = "employee_id, start_date"),
                @Index(name = "idx_leave_request__status__start_date",
                        columnList = "status, start_date")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_code", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 주말·공휴일을 제외하고 계산한 신청 일수 스냅샷. 반차는 0.5(5.1, FR-32). */
    @Column(name = "days", nullable = false, precision = 4, scale = 1)
    private BigDecimal days;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveStatus status;

    /** 팀장이 최소 잔류 인원 초과를 허용한 표시. 이 플래그가 없으면 관리자도 초과 승인을 할 수 없다(6.3). */
    @Column(name = "staff_limit_override", nullable = false)
    private boolean staffLimitOverride;

    /** 팀장 대신 1차 확인을 맡은 상위 부서장. 일반 흐름에서는 {@code null}(6.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to")
    private Employee escalatedTo;

    @Builder
    private LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                         BigDecimal days, String reason, LeaveStatus status,
                         boolean staffLimitOverride, Employee escalatedTo) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
        this.status = status;
        this.staffLimitOverride = staffLimitOverride;
        this.escalatedTo = escalatedTo;
    }
}
