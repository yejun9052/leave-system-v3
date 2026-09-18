package com.leavesystem.leave.domain.holiday;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 공휴일·사내휴일·신청 금지 기간. 설계 문서 5.3, 7장.
 *
 * <p>하루짜리 휴일도 {@code startDate == endDate} 인 기간으로 표현한다.
 * {@link HolidayType#BLOCKED} 기간은 서버에서 신청 자체를 거부한다(FR-33).
 */
@Entity
@Getter
@Table(
        name = "holiday",
        indexes = @Index(name = "idx_holiday__start_date__end_date", columnList = "start_date, end_date")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private HolidayType type;

    @Builder
    private Holiday(LocalDate startDate, LocalDate endDate, String name, HolidayType type) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.name = name;
        this.type = type;
    }
}
