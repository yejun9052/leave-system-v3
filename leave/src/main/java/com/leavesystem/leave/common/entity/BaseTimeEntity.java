package com.leavesystem.leave.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 등록·수정 시각을 공통으로 관리하는 상위 클래스.
 *
 * <p>Spring Data JPA Auditing 을 사용한다. 동작하려면
 * {@link com.leavesystem.leave.common.config.JpaAuditingConfig} 의 {@code @EnableJpaAuditing} 이 필요하다.
 *
 * <p>JPA 엔티티 콜백 기반이므로 JPQL·네이티브 벌크 UPDATE 에는 적용되지 않는다.
 * 벌크 갱신 시에는 {@code updated_at} 을 쿼리에서 직접 갱신한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
