package com.company.leave.notification;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.notification.dto.NotificationResponse;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록")
    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(notificationService.list(SecurityUtils.currentEmployeeId(), limit));
    }

    @Operation(summary = "안 읽은 알림 수")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        long count = notificationService.unreadCount(SecurityUtils.currentEmployeeId());
        return ApiResponse.ok(Map.of("count", count));
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        notificationService.markAllRead(SecurityUtils.currentEmployeeId());
        return ApiResponse.ok();
    }
}
