package com.company.leave.notification;

import com.company.leave.notification.domain.Notification;
import com.company.leave.notification.dto.NotificationResponse;
import com.company.leave.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(Long employeeId, String type, String title, String message, String link) {
        notificationRepository.save(new Notification(employeeId, type, title, message, link));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long employeeId, int limit) {
        return notificationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId, PageRequest.of(0, limit))
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long employeeId) {
        return notificationRepository.countByEmployeeIdAndReadFalse(employeeId);
    }

    @Transactional
    public void markAllRead(Long employeeId) {
        notificationRepository.markAllRead(employeeId);
    }
}
