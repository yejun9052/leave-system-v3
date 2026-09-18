package com.leavesystem.leave.domain.log;

import com.leavesystem.leave.domain.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 이벤트로그. 설계 문서 9.3, 7장.
 *
 * <p>append-only 이므로 수정 시각을 두지 않고 {@link com.leavesystem.leave.common.entity.BaseTimeEntity}
 * 를 상속하지 않는다. 기록 시각만 JPA Auditing 으로 채운다.
 * 업무 변경과 같은 트랜잭션에 기록한다(8장).
 */
@Entity
@Getter
@Table(
        name = "action_log",
        indexes = {
                @Index(name = "idx_action_log__target_type__target_id__created_at",
                        columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_action_log__actor_id__created_at",
                        columnList = "actor_id, created_at"),
                @Index(name = "idx_action_log__created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 행위자. 스케줄러 등 시스템 행위는 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Employee actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, length = 30)
    private ActionType action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 30)
    private TargetType targetType;

    /** 대상 식별자. 대상 종류가 다양해 연관관계 대신 값으로 보관한다. */
    @Column(name = "target_id")
    private Long targetId;

    /** 변경 전후 값 등 상세 정보(JSON). 전문 검색은 범위에서 제외한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ActionLog(Employee actor, ActionType action, TargetType targetType, Long targetId, String detail) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }
}
