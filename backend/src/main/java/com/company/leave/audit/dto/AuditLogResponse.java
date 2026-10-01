package com.company.leave.audit.dto;

import com.company.leave.audit.AuditLabels;
import com.company.leave.audit.domain.AuditLog;
import java.time.Instant;

/**
 * @param actionLabel 동작의 한글 표시 이름(예: 로그인, 강제 취소). 모르는 코드면 코드 그대로
 * @param entityLabel 대상의 한글 표시 이름(예: 휴가 신청)
 */
public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String actionLabel,
        String entityType,
        String entityLabel,
        String entityId,
        String detail,
        boolean success,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorName(), a.getAction(),
                AuditLabels.action(a.getAction()), a.getEntityType(), AuditLabels.resource(a.getEntityType()),
                a.getEntityId(), a.getDetail(), a.isSuccess(), a.getCreatedAt());
    }
}
