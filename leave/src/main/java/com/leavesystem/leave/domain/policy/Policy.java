package com.leavesystem.leave.domain.policy;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 운영 정책. 설계 문서 7장, 13장.
 *
 * <p>단일 행(id = {@link #SINGLETON_ID})이며 설정 항목마다 타입이 분명한 컬럼을 둔다.
 * 컬럼 추가는 Flyway 로 관리하고 기존 행을 고려해 {@code NOT NULL DEFAULT} 를 지정한다.
 */
@Entity
@Getter
@Table(name = "policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends BaseTimeEntity {

    /** 정책 행은 하나뿐이며 이 값으로 조회한다. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    /** 연차 부여 기준. MVP 로직은 {@link GrantBasis#HIRE_DATE} 만 구현한다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "grant_basis", nullable = false, length = 20)
    private GrantBasis grantBasis;

    /** 연차 사용 촉진 메일 자동 발송 여부. */
    @Column(name = "promote_enabled", nullable = false)
    private boolean promoteEnabled;

    /** 소멸 몇 개월 전에 촉진 메일을 보낼지. */
    @Column(name = "promote_months", nullable = false)
    private int promoteMonths;

    /** 정기 DB 백업 사용 여부. */
    @Column(name = "backup_enabled", nullable = false)
    private boolean backupEnabled;

    /** 정기 DB 백업 실행 주기(cron 표현식). */
    @Column(name = "backup_cron", nullable = false, length = 100)
    private String backupCron;
}
