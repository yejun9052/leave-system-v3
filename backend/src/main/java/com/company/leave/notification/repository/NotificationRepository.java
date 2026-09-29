package com.company.leave.notification.repository;

import com.company.leave.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    long countByEmployeeIdAndReadFalse(Long employeeId);

    @Modifying
    @Query("update Notification n set n.read = true where n.employeeId = :employeeId and n.read = false")
    int markAllRead(@Param("employeeId") Long employeeId);
}
