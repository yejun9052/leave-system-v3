package com.company.leave.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.dto.CalendarDtos;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 캘린더 조회: 누가 어떤 일정을 보고 고칠 수 있는지, 날짜 상세에 무엇이 담기는지. */
@ExtendWith(MockitoExtension.class)
@DisplayName("캘린더 일정 보기 범위")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CalendarServiceVisibilityTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final Long ME = 30L;
    private static final Long COLLEAGUE = 31L;
    private static final Long MY_DEPT = 3L;
    private static final Long OTHER_DEPT = 5L;

    @Mock private CalendarEventRepository events;
    @Mock private HolidayRepository holidays;
    @Mock private DepartmentRepository departments;
    @Mock private LeaveRequestService leaveRequests;
    @Mock private AnnouncementMessenger announcements;

    private CalendarService service;

    // 전사·내 부서·다른 부서·내 개인·동료 개인 일정
    private final CalendarEvent 전사 = event(1L, CalendarEventScope.COMPANY, null, null, ME);
    private final CalendarEvent 내부서 = event(2L, CalendarEventScope.DEPARTMENT, MY_DEPT, null, COLLEAGUE);
    private final CalendarEvent 다른부서 = event(3L, CalendarEventScope.DEPARTMENT, OTHER_DEPT, null, COLLEAGUE);
    private final CalendarEvent 내개인 = event(4L, CalendarEventScope.PERSONAL, null, ME, ME);
    private final CalendarEvent 동료개인 = event(5L, CalendarEventScope.PERSONAL, null, COLLEAGUE, COLLEAGUE);

    @BeforeEach
    void setUp() {
        service = new CalendarService(events, holidays, departments, leaveRequests, announcements);
    }

    @Nested
    @DisplayName("기간 조회")
    class 기간_조회 {

        @BeforeEach
        void 일정이_있다() {
            when(events.findBetween(DAY, DAY)).thenReturn(List.of(전사, 내부서, 다른부서, 내개인, 동료개인));
            when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of());
        }

        @Test
        void 직원은_전사_일정과_자기_부서_일정과_자기_개인_일정만_본다() {
            List<CalendarDtos.EventResponse> visible = service.getEvents(DAY, DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

            assertThat(visible).extracting(CalendarDtos.EventResponse::id).containsExactly("E1", "E2", "E4");
        }

        @Test
        void 관리자는_모든_부서와_개인_일정을_본다() {
            List<CalendarDtos.EventResponse> visible = service.getEvents(DAY, DAY, employee(10L, null, Role.HR_ADMIN));

            assertThat(visible).extracting(CalendarDtos.EventResponse::id)
                    .containsExactly("E1", "E2", "E3", "E4", "E5");
        }

        @Test
        void 수정_가능_표시는_만든_사람에게만_붙고_관리자는_모두_고칠_수_있다() {
            List<CalendarDtos.EventResponse> mine = service.getEvents(DAY, DAY, employee(ME, MY_DEPT, Role.TEAM_LEAD));
            List<CalendarDtos.EventResponse> admin = service.getEvents(DAY, DAY, employee(10L, null, Role.HR_ADMIN));

            assertThat(mine).extracting(CalendarDtos.EventResponse::id, CalendarDtos.EventResponse::editable)
                    .containsExactly(tuple("E1", true), tuple("E2", false), tuple("E4", true));
            assertThat(admin).allMatch(CalendarDtos.EventResponse::editable);
        }
    }

    @Test
    void 휴가_일정에는_휴가를_낸_직원과_부서가_들어가고_공휴일에는_없다() {
        CalendarEvent 휴가 = CalendarEvent.builder().title("홍길동 - 연차").startDate(DAY).endDate(DAY)
                .scope(CalendarEventScope.COMPANY).source(CalendarEventSource.LEAVE_REQUEST)
                .employeeId(ME).departmentId(MY_DEPT).build();
        ReflectionTestUtils.setField(휴가, "id", 9L);
        when(events.findBetween(DAY, DAY)).thenReturn(List.of(휴가));
        when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of(holiday(1L, DAY, "임시공휴일")));

        List<CalendarDtos.EventResponse> visible = service.getEvents(DAY, DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

        assertThat(visible).extracting(CalendarDtos.EventResponse::id, CalendarDtos.EventResponse::employeeId,
                        CalendarDtos.EventResponse::departmentId)
                .containsExactly(tuple("E9", ME, MY_DEPT), tuple("H1", null, null));
    }

    @Test
    void 휴가에서_만든_일정은_관리자에게도_수정_불가로_표시된다() {
        CalendarEvent 휴가 = CalendarEvent.builder().title("홍길동 - 연차").startDate(DAY).endDate(DAY)
                .scope(CalendarEventScope.COMPANY).source(CalendarEventSource.LEAVE_REQUEST).createdBy(10L).build();
        ReflectionTestUtils.setField(휴가, "id", 9L);
        when(events.findBetween(DAY, DAY)).thenReturn(List.of(휴가));
        when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of());

        List<CalendarDtos.EventResponse> visible = service.getEvents(DAY, DAY, employee(10L, null, Role.HR_ADMIN));

        assertThat(visible).singleElement().satisfies(e -> assertThat(e.editable()).isFalse());
    }

    @Test
    void 공휴일을_수정_불가_일정으로_함께_내려준다() {
        Holiday 한글날 = holiday(7L, DAY, "한글날");
        when(events.findBetween(DAY, DAY)).thenReturn(List.of());
        when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of(한글날));

        List<CalendarDtos.EventResponse> visible = service.getEvents(DAY, DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

        assertThat(visible).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo("H7");
            assertThat(e.title()).isEqualTo("한글날");
            assertThat(e.source()).isEqualTo(CalendarEventSource.HOLIDAY);
            assertThat(e.editable()).isFalse();
            assertThat(e.end()).isEqualTo(DAY.plusDays(1));
        });
    }

    @Nested
    @DisplayName("날짜 상세")
    class 날짜_상세 {

        @Test
        void 휴가에서_만든_일정은_빼고_볼_수_있는_등록_일정만_담는다() {
            CalendarEvent 휴가 = CalendarEvent.builder().title("홍길동 - 연차").startDate(DAY).endDate(DAY)
                    .scope(CalendarEventScope.COMPANY).source(CalendarEventSource.LEAVE_REQUEST).build();
            ReflectionTestUtils.setField(휴가, "id", 9L);
            when(events.findBetween(DAY, DAY)).thenReturn(List.of(전사, 다른부서, 휴가));
            when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of());

            CalendarDtos.DayDetail detail = service.getDay(DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

            assertThat(detail.events()).extracting(CalendarDtos.DayEvent::id).containsExactly("E1");
            assertThat(detail.holidayName()).isNull();
        }

        @Test
        void 같은_날_공휴일이_여럿이면_이름을_쉼표로_잇는다() {
            when(events.findBetween(DAY, DAY)).thenReturn(List.of());
            when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY))
                    .thenReturn(List.of(holiday(1L, DAY, "추석"), holiday(2L, DAY, "대체공휴일")));

            CalendarDtos.DayDetail detail = service.getDay(DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

            assertThat(detail.holidayName()).isEqualTo("추석, 대체공휴일");
        }

        @Test
        void 휴가_목록은_조회한_사람_기준으로_받은_그대로_담는다() {
            List<LeaveRequestDtos.DayLeave> leaves = List.of(new LeaveRequestDtos.DayLeave(100L, ME, "나", MY_DEPT,
                    "개발팀", "연차", "#4f46e5", null, null, DAY, DAY, null, true));
            when(events.findBetween(DAY, DAY)).thenReturn(List.of());
            when(holidays.findByDateBetweenOrderByDateAsc(DAY, DAY)).thenReturn(List.of());
            when(leaveRequests.leavesOnDay(ME, DAY)).thenReturn(leaves);

            CalendarDtos.DayDetail detail = service.getDay(DAY, employee(ME, MY_DEPT, Role.EMPLOYEE));

            assertThat(detail.date()).isEqualTo(DAY);
            assertThat(detail.leaves()).isSameAs(leaves);
        }
    }

    private static CalendarEvent event(Long id, CalendarEventScope scope, Long departmentId, Long employeeId,
                                       Long createdBy) {
        CalendarEvent e = CalendarEvent.builder().title("일정" + id).startDate(DAY).endDate(DAY).scope(scope)
                .source(CalendarEventSource.ADMIN_EVENT).departmentId(departmentId).employeeId(employeeId)
                .createdBy(createdBy).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static Holiday holiday(Long id, LocalDate date, String name) {
        Holiday h = new Holiday(date, name);
        ReflectionTestUtils.setField(h, "id", id);
        return h;
    }

    private static UserPrincipal employee(Long id, Long departmentId, Role role) {
        Department department = null;
        if (departmentId != null) {
            department = new Department("부서" + departmentId, null, 0);
            ReflectionTestUtils.setField(department, "id", departmentId);
        }
        Employee e = Employee.builder().email("user" + id + "@company.com").name("직원")
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1)).department(department)
                .roles(Set.of(role)).build();
        ReflectionTestUtils.setField(e, "id", id);
        return UserPrincipal.from(e);
    }
}
