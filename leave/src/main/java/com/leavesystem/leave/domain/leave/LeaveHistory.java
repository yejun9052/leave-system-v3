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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 연차 원장. 설계 문서 5.1, 7장.
 *
 * <p>잔여 연차는 사원별 {@code days} 합계로 계산하며 별도의 잔여 컬럼을 두지 않는다.
 * {@code UNIQUE(request_id, type)}으로 같은 신청의 같은 유형이 중복 기록되는 것을 막는다(8장).
 */
@Entity
@Getter
@Table(
        name = "leave_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_history__request_id__type",
                columnNames = {"request_id", "type"}),
        indexes = @Index(
                name = "idx_leave_history__employee_id__occurred_on",
                columnList = "employee_id, occurred_on")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private LeaveHistoryType type;

    /** 증감 일수. 부여·취소는 양수, 사용·소멸은 음수. */
    @Column(name = "days", nullable = false, precision = 4, scale = 1)
    private BigDecimal days;

    /** 원장 발생일. */
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** 원장을 발생시킨 신청. 부여·소멸·정정처럼 신청과 무관하면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private LeaveRequest request;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", nullable = false, length = 20)
    private LeaveHistorySource source;

    @Column(name = "memo", length = 500)
    private String memo;

    @Builder
    private LeaveHistory(Employee employee, LeaveHistoryType type, BigDecimal days, LocalDate occurredOn,
                         LeaveRequest request, LeaveHistorySource source, String memo) {
        this.employee = employee;
        this.type = type;
        this.days = days;
        this.occurredOn = occurredOn;
        this.request = request;
        this.source = source;
        this.memo = memo;
    }
}
