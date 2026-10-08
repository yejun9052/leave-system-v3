package com.company.leave.backup;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.AnnouncementMail;
import com.company.leave.mail.BackupMailTemplates;
import com.company.leave.notification.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백업·복원 안내: 재직 중인 시스템 관리자·인사관리자에게 앱 알림과 메일(숨은 참조 한 통, 커밋 뒤 발송).
 */
@Component
public class BackupMessenger {

    static final String LINK = "/admin/policy";
    private static final int MAX_MESSAGE = 500;

    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final AccountMailProperties mailProperties;
    private final Clock clock;

    @Autowired
    public BackupMessenger(EmployeeRepository employeeRepository, NotificationService notificationService,
                           ApplicationEventPublisher events, AccountMailProperties mailProperties) {
        this(employeeRepository, notificationService, events, mailProperties, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    BackupMessenger(EmployeeRepository employeeRepository, NotificationService notificationService,
                    ApplicationEventPublisher events, AccountMailProperties mailProperties, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.events = events;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    @Transactional
    public void autoBackupFailed(String reason) {
        String why = reason == null || reason.isBlank() ? "알 수 없는 오류" : reason;
        deliver("BACKUP_FAILED", "자동 백업 실패", why,
                BackupMailTemplates.autoBackupFailed(LocalDateTime.now(clock), why, mailProperties.linkBaseUrl()));
    }

    /** 복원 완료: 복원된 DB 기준의 시스템 관리자·인사관리자(복원한 본인 포함)에게. */
    @Transactional
    public void restored(String actorName, String fileName, LocalDateTime backupAt, String preRestoreFile) {
        deliver("BACKUP_RESTORED", "데이터 복원",
                actorName + "님이 " + backupAt.toLocalDate() + " " + backupAt.toLocalTime() + " 시점 백업으로 복원했습니다. "
                        + "복원 전 백업: " + preRestoreFile,
                BackupMailTemplates.restored(actorName, fileName, backupAt, preRestoreFile, LocalDateTime.now(clock),
                        mailProperties.linkBaseUrl()));
    }

    private void deliver(String type, String title, String message, BackupMailTemplates.Mail mail) {
        List<Employee> admins = employeeRepository.findAllById(employeeRepository.findIdsByAnyRoleAndStatus(
                List.of(Role.SYSTEM_ADMIN, Role.HR_ADMIN), EmployeeStatus.ACTIVE));
        String text = message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
        admins.forEach(e -> notificationService.notify(e.getId(), type, title, text, LINK));
        List<String> bcc = admins.stream()
                .map(Employee::getEmail)
                .filter(email -> email != null && email.contains("@"))
                .distinct()
                .toList();
        if (!bcc.isEmpty()) {
            events.publishEvent(new AnnouncementMail(bcc, mail.subject(), mail.content().text(), mail.content().html()));
        }
    }
}
