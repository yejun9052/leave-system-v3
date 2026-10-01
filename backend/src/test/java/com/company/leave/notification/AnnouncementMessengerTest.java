package com.company.leave.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.AnnouncementMail;
import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.notification.AnnouncementMessenger.EventView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
@DisplayName("블랙아웃·일정 공지 받는 사람")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnouncementMessengerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 28);

    @Mock private EmployeeRepository employees;
    @Mock private DepartmentRepository departments;
    @Mock private NotificationService notifications;
    @Mock private ApplicationEventPublisher publisher;

    private AnnouncementMessenger messenger;

    private final Department QA팀 = 부서(3L, "QA팀");
    private final Department 개발팀 = 부서(4L, "개발팀");
    private Employee 인사관리자;
    private Employee 큐에이1;
    private Employee 큐에이2;
    private Employee 개발1;

    @BeforeEach
    void setUp() {
        messenger = new AnnouncementMessenger(employees, departments, notifications, publisher,
                new AccountMailProperties("no-reply@company.com", "http://localhost"));
        인사관리자 = 직원(1L, null, "hr@company.com", Role.HR_ADMIN);
        큐에이1 = 직원(11L, QA팀, "qa1@company.com", Role.EMPLOYEE);
        큐에이2 = 직원(12L, QA팀, null, Role.EMPLOYEE); // 이메일 없음: 알림만
        개발1 = 직원(21L, 개발팀, "dev1@company.com", Role.EMPLOYEE);
        lenient().when(employees.findById(1L)).thenReturn(Optional.of(인사관리자));
        lenient().when(employees.findByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE))
                .thenReturn(List.of(인사관리자, 큐에이1, 큐에이2, 개발1));
        lenient().when(employees.findByDepartmentId(3L)).thenReturn(List.of(큐에이1, 큐에이2));
        lenient().when(employees.findByDepartmentId(4L)).thenReturn(List.of(개발1));
        lenient().when(departments.findById(3L)).thenReturn(Optional.of(QA팀));
        lenient().when(departments.findById(4L)).thenReturn(Optional.of(개발팀));
    }

    @Test
    void 블랙아웃은_처리한_본인을_뺀_전_직원에게_알리고_메일은_주소_있는_사람만_한_통으로_보낸다() {
        messenger.blackout(Change.CREATED, new Schedule("결산 주간", DAY, DAY, null), null, 1L);

        verify(notifications).notify(eq(11L), eq("BLACKOUT_CREATED"), anyString(), anyString(), eq("/calendar"));
        verify(notifications).notify(eq(12L), eq("BLACKOUT_CREATED"), anyString(), anyString(), eq("/calendar"));
        verify(notifications).notify(eq(21L), eq("BLACKOUT_CREATED"), anyString(), anyString(), eq("/calendar"));
        verify(notifications, never()).notify(eq(1L), anyString(), anyString(), anyString(), anyString());
        AnnouncementMail mail = 보낸_메일();
        assertThat(mail.bcc()).containsExactly("qa1@company.com", "dev1@company.com");
        assertThat(mail.body()).contains("처리자: 직원1 (인사관리자)");
    }

    @Test
    void 부서_일정은_그_부서_소속에게만_간다() {
        messenger.event(Change.CREATED, 일정(CalendarEventScope.DEPARTMENT, 3L), null, 1L);

        assertThat(보낸_메일().bcc()).containsExactly("qa1@company.com");
        verify(notifications).notify(eq(12L), eq("CALENDAR_EVENT_CREATED"), eq("QA팀 일정 추가"), anyString(), anyString());
        verify(notifications, never()).notify(eq(21L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 부서_일정을_다른_부서로_옮기면_옮기기_전과_후_부서_모두에게_간다() {
        messenger.event(Change.UPDATED, 일정(CalendarEventScope.DEPARTMENT, 4L),
                일정(CalendarEventScope.DEPARTMENT, 3L), 1L);

        AnnouncementMail mail = 보낸_메일();
        assertThat(mail.bcc()).containsExactlyInAnyOrder("qa1@company.com", "dev1@company.com");
        assertThat(mail.body()).contains("범위: 개발팀 일정").contains("변경 전").contains("범위: QA팀 일정");
    }

    @Test
    void 개인_일정은_아무에게도_보내지_않는다() {
        messenger.event(Change.CREATED, 일정(CalendarEventScope.PERSONAL, null), null, 1L);

        verifyNoInteractions(notifications, publisher);
    }

    @Test
    void 바뀐_내용이_없으면_보내지_않는다() {
        messenger.event(Change.UPDATED, 일정(CalendarEventScope.COMPANY, null), 일정(CalendarEventScope.COMPANY, null), 1L);
        messenger.blackout(Change.UPDATED, new Schedule("결산", DAY, DAY, null), new Schedule("결산", DAY, DAY, null), 1L);

        verifyNoInteractions(notifications, publisher);
    }

    @Test
    void 퇴사자와_관리_전용_계정은_받지_않는다() {
        Employee 퇴사자 = 직원(31L, QA팀, "gone@company.com", Role.EMPLOYEE);
        ReflectionTestUtils.setField(퇴사자, "status", EmployeeStatus.RESIGNED);
        Employee 관리계정 = 직원(32L, QA팀, "admin@company.com", Role.SYSTEM_ADMIN);
        ReflectionTestUtils.setField(관리계정, "systemAccount", true);
        when(employees.findByDepartmentId(3L)).thenReturn(List.of(큐에이1, 퇴사자, 관리계정));

        messenger.event(Change.DELETED, 일정(CalendarEventScope.DEPARTMENT, 3L), null, 1L);

        assertThat(보낸_메일().bcc()).containsExactly("qa1@company.com");
        verify(notifications, never()).notify(eq(31L), anyString(), anyString(), anyString(), anyString());
        verify(notifications, never()).notify(eq(32L), anyString(), anyString(), anyString(), anyString());
    }

    private AnnouncementMail 보낸_메일() {
        ArgumentCaptor<AnnouncementMail> captor = ArgumentCaptor.forClass(AnnouncementMail.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static EventView 일정(CalendarEventScope scope, Long departmentId) {
        return new EventView("워크숍", DAY, DAY, scope, departmentId);
    }

    private static Department 부서(Long id, String name) {
        Department department = new Department(name, null, 0);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    private static Employee 직원(Long id, Department department, String email, Role role) {
        Employee employee = Employee.builder()
                .email(email).passwordHash("h").name("직원" + id)
                .department(department).roles(Set.of(role))
                .build();
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
