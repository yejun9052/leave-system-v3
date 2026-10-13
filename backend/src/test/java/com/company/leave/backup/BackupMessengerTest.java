package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.AnnouncementMail;
import com.company.leave.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("백업·복원 안내 받는 사람")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupMessengerTest {

    private static final LocalDateTime BACKUP_AT = LocalDateTime.of(2026, 10, 1, 4, 0, 12);

    @Mock private EmployeeRepository employees;
    @Mock private NotificationService notifications;
    @Mock private ApplicationEventPublisher publisher;

    private BackupMessenger messenger;

    private final Employee 시스템관리자 = 직원(1L, "admin", Role.SYSTEM_ADMIN);
    private final Employee 인사관리자 = 직원(2L, "hr@company.com", Role.HR_ADMIN);
    private final Employee 직원1 = 직원(11L, "e1@company.com", Role.EMPLOYEE);
    private final Employee 직원2 = 직원(12L, null, Role.EMPLOYEE); // 이메일 없음: 알림만

    @BeforeEach
    void setUp() {
        messenger = new BackupMessenger(employees, notifications, publisher,
                new AccountMailProperties("no-reply@company.com", "https://leave.company.com"),
                Clock.fixed(Instant.parse("2026-10-13T06:30:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 복원_완료_안내는_인사관리자에게만_가고_전_직원에게는_휴가를_다시_확인하라는_안내가_간다() {
        when(employees.findIdsByAnyRoleAndStatus(List.of(Role.HR_ADMIN), EmployeeStatus.ACTIVE)).thenReturn(List.of(2L));
        when(employees.findAllById(List.of(2L))).thenReturn(List.of(인사관리자));
        when(employees.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(시스템관리자, 인사관리자, 직원1, 직원2));

        messenger.restored("시스템관리자", "auto-20261001-0400.dump", BACKUP_AT, "pre-restore-20261013-1530.dump");

        // 완료 안내: 인사관리자만(시스템 관리자는 받지 않음)
        verify(notifications).notify(eq(2L), eq("BACKUP_RESTORED"), anyString(), anyString(), eq("/admin/policy"));
        verify(notifications, never()).notify(eq(1L), eq("BACKUP_RESTORED"), anyString(), anyString(), anyString());
        // 복원 안내: 재직 중인 전 직원(내 휴가로)
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(4)).notify(org.mockito.ArgumentMatchers.anyLong(), eq("DATA_RESTORED"),
                eq("데이터 복원"), message.capture(), eq("/my-leaves"));
        assertThat(message.getValue()).isEqualTo("2026-10-01 04:00 시점으로 복원되었습니다. 신청한 휴가를 다시 확인해 주세요.");

        List<AnnouncementMail> mails = 보낸_메일(2);
        assertThat(mails.get(0).bcc()).containsExactly("hr@company.com");
        assertThat(mails.get(0).subject()).isEqualTo("[연차관리] 데이터 복원 완료 (2026-10-01 04:00 시점)");
        assertThat(mails.get(0).text()).contains("수신자: 인사관리자").contains("처리자: 시스템관리자")
                .contains("전 직원에게 신청한 휴가를 다시 확인하라는 안내를 보냈습니다.");
        assertThat(mails.get(1).bcc()).containsExactly("hr@company.com", "e1@company.com");
        assertThat(mails.get(1).subject()).isEqualTo("[연차관리] 데이터 복원 안내 (2026-10-01 04:00 시점)");
        assertThat(mails.get(1).text())
                .contains("연차관리 데이터가 2026-10-01 04:00 시점으로 복원되었습니다.")
                .contains("이 시점 이후에 신청하거나 결재한 휴가는 사라졌을 수 있으니 내 휴가에서 다시 확인해 주세요.")
                .contains("수신자: 재직 중인 전 직원")
                .contains("내 휴가 확인하기: https://leave.company.com/my-leaves");
    }

    @Test
    void 자동_백업_실패는_지금처럼_시스템_관리자와_인사관리자에게_간다() {
        when(employees.findIdsByAnyRoleAndStatus(List.of(Role.SYSTEM_ADMIN, Role.HR_ADMIN), EmployeeStatus.ACTIVE))
                .thenReturn(List.of(1L, 2L));
        when(employees.findAllById(List.of(1L, 2L))).thenReturn(List.of(시스템관리자, 인사관리자));

        messenger.autoBackupFailed("pg_dump 실행 실패");

        verify(notifications).notify(eq(1L), eq("BACKUP_FAILED"), anyString(), anyString(), eq("/admin/policy"));
        verify(notifications).notify(eq(2L), eq("BACKUP_FAILED"), anyString(), anyString(), eq("/admin/policy"));
        assertThat(보낸_메일(1).get(0).text()).contains("수신자: 시스템 관리자·인사관리자");
    }

    private List<AnnouncementMail> 보낸_메일(int count) {
        ArgumentCaptor<AnnouncementMail> captor = ArgumentCaptor.forClass(AnnouncementMail.class);
        verify(publisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private static Employee 직원(Long id, String email, Role role) {
        Employee employee = Employee.builder()
                .email(email).passwordHash("h").name("직원" + id).role(role)
                .build();
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
