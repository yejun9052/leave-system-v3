package com.company.leave.notification.dto;

import com.company.leave.notification.domain.Notification;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(),
                n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
