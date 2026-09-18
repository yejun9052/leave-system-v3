package com.leavesystem.leave.domain.leave;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 휴가 종류. 설계 문서 5.3, 7장.
 *
 * <p>코드 자체가 식별자이며 관리자가 차감 일수·노출 조건·잔류 인원 적용 여부를 설정한다(FR-22).
 * 자유형 규칙 엔진 대신 제한된 코드·필드로만 규칙을 표현한다(13장).
 *
 * <p>기본 코드: {@code ANNUAL}, {@code HALF_AM}, {@code HALF_PM},
 * {@code SICK}, {@code OFFICIAL}, {@code FAMILY_EVENT}.
 */
@Entity
@Getter
@Table(name = "leave_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveType extends BaseTimeEntity {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 근무일 하루당 차감 일수. 반차 0.5, 병가·공가 0. */
    @Column(name = "deduct_days", nullable = false, precision = 3, scale = 1)
    private BigDecimal deductDays;

    /** 하루 신청만 허용하는지 여부. 반차가 해당한다. */
    @Column(name = "single_day_only", nullable = false)
    private boolean singleDayOnly;

    /** 잔여 연차가 0일 때만 노출할지 여부. 병가·공가가 해당한다. */
    @Column(name = "only_when_empty", nullable = false)
    private boolean onlyWhenEmpty;

    /** 최소 잔류 인원 제한 대상인지 여부. 연차·경조사가 해당한다(6.3). */
    @Column(name = "staff_limit_applied", nullable = false)
    private boolean staffLimitApplied;

    /** 신청 화면 노출 여부. 사용하지 않는 종류는 이력 보존을 위해 삭제 대신 숨긴다. */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** 신청 화면 정렬 순서. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private LeaveType(String code, String name, BigDecimal deductDays, boolean singleDayOnly,
                      boolean onlyWhenEmpty, boolean staffLimitApplied, boolean active, int sortOrder) {
        this.code = code;
        this.name = name;
        this.deductDays = deductDays;
        this.singleDayOnly = singleDayOnly;
        this.onlyWhenEmpty = onlyWhenEmpty;
        this.staffLimitApplied = staffLimitApplied;
        this.active = active;
        this.sortOrder = sortOrder;
    }
}
