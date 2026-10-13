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
 * 백업·복원 안내: 앱 알림과 메일(숨은 참조 한 통, 커밋 뒤 발송). 받는 사람은 재직 중인 사람만.
 * <ul>
 *   <li>자동 백업 실패: 시스템 관리자·인사관리자</li>
 *   <li>복원 완료: 인사관리자(복원은 시스템 관리자만 할 수 있고 본인은 화면에서 결과를 본다)</li>
 *   <li>복원 안내: 전 직원(그 시점 이후 신청·결재한 휴가를 다시 확인하라는 안내)</li>
 * </ul>
 */
@Component
public class BackupMessenger {

    static final String LINK = "/admin/policy";
    static final String MY_LEAVES = "/my-leaves";
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
        deliver(activeWithRole(Role.SYSTEM_ADMIN, Role.HR_ADMIN), "BACKUP_FAILED", "자동 백업 실패", why, LINK,
                BackupMailTemplates.autoBackupFailed(LocalDateTime.now(clock), why, mailProperties.linkBaseUrl()));
    }

    /**
     * 복원 완료(복원된 DB 기준의 재직자에게): 인사관리자에게 완료 안내, 전 직원에게 휴가를 다시 확인하라는 안내.
     */
    @Transactional
    public void restored(String actorName, String fileName, LocalDateTime backupAt, String preRestoreFile) {
        String when = BackupMailTemplates.time(backupAt);
        String baseUrl = mailProperties.linkBaseUrl();
        deliver(activeWithRole(Role.HR_ADMIN), "BACKUP_RESTORED", "데이터 복원",
                actorName + "님이 " + when + " 시점 백업으로 복원했습니다. 복원 전 백업: " + preRestoreFile, LINK,
                BackupMailTemplates.restored(actorName, fileName, backupAt, preRestoreFile, LocalDateTime.now(clock),
                        baseUrl));
        deliver(employeeRepository.findByStatus(EmployeeStatus.ACTIVE), "DATA_RESTORED", "데이터 복원",
                when + " 시점으로 복원되었습니다. 신청한 휴가를 다시 확인해 주세요.", MY_LEAVES,
                BackupMailTemplates.restoredNotice(backupAt, baseUrl));
    }

    private List<Employee> activeWithRole(Role... roles) {
        return employeeRepository.findAllById(
                employeeRepository.findIdsByAnyRoleAndStatus(List.of(roles), EmployeeStatus.ACTIVE));
    }

    private void deliver(List<Employee> recipients, String type, String title, String message, String link,
                         BackupMailTemplates.Mail mail) {
        String text = message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
        recipients.forEach(e -> notificationService.notify(e.getId(), type, title, text, link));
        List<String> bcc = recipients.stream()
                .map(Employee::getEmail)
                .filter(email -> email != null && email.contains("@"))
                .distinct()
                .toList();
        if (!bcc.isEmpty()) {
            events.publishEvent(new AnnouncementMail(bcc, mail.subject(), mail.content().text(), mail.content().html()));
        }
    }
}
