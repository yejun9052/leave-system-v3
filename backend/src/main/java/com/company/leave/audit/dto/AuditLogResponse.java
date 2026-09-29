package com.company.leave.audit.dto;

import com.company.leave.audit.domain.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String entityType,
        String entityId,
        String detail,
        boolean success,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorName(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetail(), a.isSuccess(), a.getCreatedAt());
    }
}
