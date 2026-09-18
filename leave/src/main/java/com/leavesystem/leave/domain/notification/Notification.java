package com.leavesystem.leave.domain.notification;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.leave.LeaveRequest;
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

import java.time.LocalDateTime;

/**
 * 앱 내 알림. 설계 문서 9.1, 7장.
 *
 * <p>알림 행은 현재 상태(읽음·메일 발송 여부)를 담고, 발송·재발송 이력은 이벤트로그가 담당한다.
 * 메일은 알림 저장 트랜잭션 커밋 이후에 비동기로 보내고 결과를 이 행에 기록한다.
 */
@Entity
@Getter
@Table(
        name = "notification",
        indexes = @Index(name = "idx_notification__receiver_id__is_read__id",
                columnList = "receiver_id, is_read, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Employee receiver;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    /** 관련 휴가 신청. 공지 게시 알림처럼 신청과 무관하면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private LeaveRequest request;

    /** 알림 목록에 표시할 문구. */
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /**
     * 읽음 여부.
     *
     * <p>컬럼명이 {@code is_read} 인 이유: {@code READ} 는 MySQL 예약어다.
     */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "mail_sent", nullable = false)
    private boolean mailSent;

    @Column(name = "mail_sent_at")
    private LocalDateTime mailSentAt;

    @Builder
    private Notification(Employee receiver, NotificationType type, LeaveRequest request, String message) {
        this.receiver = receiver;
        this.type = type;
        this.request = request;
        this.message = message;
        this.read = false;
        this.mailSent = false;
    }
}
