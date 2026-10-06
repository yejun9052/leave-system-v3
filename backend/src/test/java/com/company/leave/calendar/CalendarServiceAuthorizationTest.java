package com.company.leave.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.calendar.dto.CalendarDtos;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 일정 등록·수정·삭제 권한과 입력 검사(범위 선택지·공지는 CalendarServiceEventScopesTest). */
@ExtendWith(MockitoExtension.class)
@DisplayName("캘린더 일정 등록·수정·삭제 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CalendarServiceAuthorizationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final Long HR = 10L;
    private static final Long LEAD = 20L;
    private static final Long OTHER_LEAD = 21L;

    @Mock private CalendarEventRepository events;
    @Mock private HolidayRepository holidays;
    @Mock private DepartmentRepository departments;
    @Mock private LeaveRequestService leaveRequests;
    @Mock private AnnouncementMessenger announcements;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(events, holidays, departments, leaveRequests, announcements);
    }

    @Nested
    @DisplayName("등록")
    class 등록 {

        @Test
        void 팀장은_전사_일정을_등록할_수_없다() {
            assertThatThrownBy(() -> service.create(request(CalendarEventScope.COMPANY, null), user(LEAD, Role.TEAM_LEAD)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            verify(events, never()).save(any());
        }

        @Test
        void 팀장은_맡은_부서의_하위_부서_일정을_등록할_수_있고_만든_사람으로_남는다() {
            leads(LEAD, 2L, List.of(2L, 3L, 4L));

            CalendarDtos.EventResponse created =
                    service.create(request(CalendarEventScope.DEPARTMENT, 3L), user(LEAD, Role.TEAM_LEAD));

            CalendarEvent saved = savedEvent();
            assertThat(saved.getDepartmentId()).isEqualTo(3L);
            assertThat(saved.getCreatedBy()).isEqualTo(LEAD);
            assertThat(saved.getSource()).isEqualTo(CalendarEventSource.ADMIN_EVENT);
            assertThat(created.editable()).isTrue();
        }

        @Test
        void 전사_일정은_부서를_비우고_색을_주지_않으면_기본_색으로_저장한다() {
            service.create(request(CalendarEventScope.COMPANY, 5L), user(HR, Role.HR_ADMIN));

            CalendarEvent saved = savedEvent();
            assertThat(saved.getDepartmentId()).isNull();
            assertThat(saved.getColorHex()).isEqualTo("#4f46e5");
        }

        @Test
        void 부서를_고르지_않은_부서_일정은_등록할_수_없다() {
            assertThatThrownBy(() -> service.create(request(CalendarEventScope.DEPARTMENT, null), user(HR, Role.HR_ADMIN)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
            verify(events, never()).save(any());
        }

        @Test
        void 종료일이_시작일보다_빠르면_등록할_수_없다() {
            CalendarDtos.CreateEvent req = new CalendarDtos.CreateEvent("워크숍", DAY, DAY.minusDays(1), true,
                    CalendarEventScope.COMPANY, null, null);

            assertThatThrownBy(() -> service.create(req, user(HR, Role.HR_ADMIN)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
            verify(events, never()).save(any());
        }
    }

    @Nested
    @DisplayName("수정·삭제")
    class 수정_삭제 {

        @Test
        void 관리자가_아니면_남이_만든_일정은_수정할_수_없다() {
            CalendarEvent 남의일정 = adminEvent(1L, OTHER_LEAD, 3L);
            when(events.findById(1L)).thenReturn(Optional.of(남의일정));

            assertThatThrownBy(() -> service.update(1L, request(CalendarEventScope.DEPARTMENT, 3L), user(LEAD, Role.TEAM_LEAD)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(남의일정.getTitle()).isEqualTo("기존 일정");
        }

        @Test
        void 관리자가_아니면_남이_만든_일정은_삭제할_수_없다() {
            CalendarEvent 남의일정 = adminEvent(1L, OTHER_LEAD, 3L);
            when(events.findById(1L)).thenReturn(Optional.of(남의일정));

            assertThatThrownBy(() -> service.delete(1L, user(LEAD, Role.TEAM_LEAD)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            verify(events, never()).delete(any());
        }

        @Test
        void 만든_팀장은_자기_일정을_삭제할_수_있다() {
            CalendarEvent 내일정 = adminEvent(1L, LEAD, 3L);
            when(events.findById(1L)).thenReturn(Optional.of(내일정));

            service.delete(1L, user(LEAD, Role.TEAM_LEAD));

            verify(events).delete(내일정);
        }

        @Test
        void 관리자는_남이_만든_일정도_삭제할_수_있다() {
            CalendarEvent 팀장일정 = adminEvent(1L, LEAD, 3L);
            when(events.findById(1L)).thenReturn(Optional.of(팀장일정));

            service.delete(1L, user(HR, Role.HR_ADMIN));

            verify(events).delete(팀장일정);
        }

        @Test
        void 팀장은_자기_일정이라도_맡지_않은_부서로_옮길_수_없다() {
            CalendarEvent 내일정 = adminEvent(1L, LEAD, 3L);
            when(events.findById(1L)).thenReturn(Optional.of(내일정));
            leads(LEAD, 2L, List.of(2L, 3L, 4L));

            assertThatThrownBy(() -> service.update(1L, request(CalendarEventScope.DEPARTMENT, 5L), user(LEAD, Role.TEAM_LEAD)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(내일정.getDepartmentId()).isEqualTo(3L);
        }

        @Test
        void 휴가에서_만든_일정은_관리자라도_직접_수정할_수_없다() {
            CalendarEvent 휴가일정 = CalendarEvent.builder().title("홍길동 - 연차").startDate(DAY).endDate(DAY)
                    .scope(CalendarEventScope.COMPANY).source(CalendarEventSource.LEAVE_REQUEST).build();
            when(events.findById(1L)).thenReturn(Optional.of(휴가일정));

            assertThatThrownBy(() -> service.update(1L, request(CalendarEventScope.COMPANY, null), user(HR, Role.HR_ADMIN)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
            assertThat(휴가일정.getTitle()).isEqualTo("홍길동 - 연차");
        }

        @Test
        void 없는_일정은_수정할_수_없다() {
            when(events.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, request(CalendarEventScope.COMPANY, null), user(HR, Role.HR_ADMIN)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        }
    }

    private void leads(Long leadId, Long departmentId, List<Long> subtree) {
        Department led = new Department("제품개발팀", null, 0);
        ReflectionTestUtils.setField(led, "id", departmentId);
        when(departments.findByLeadId(leadId)).thenReturn(List.of(led));
        when(departments.findSubtreeIds(departmentId)).thenReturn(subtree);
    }

    private CalendarEvent savedEvent() {
        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }

    private static CalendarDtos.CreateEvent request(CalendarEventScope scope, Long departmentId) {
        return new CalendarDtos.CreateEvent("워크숍", DAY, DAY, true, scope, departmentId, null);
    }

    private static CalendarEvent adminEvent(Long id, Long createdBy, Long departmentId) {
        CalendarEvent e = CalendarEvent.builder().title("기존 일정").startDate(DAY).endDate(DAY)
                .scope(CalendarEventScope.DEPARTMENT).source(CalendarEventSource.ADMIN_EVENT)
                .departmentId(departmentId).createdBy(createdBy).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static UserPrincipal user(Long id, Role role) {
        Employee e = Employee.builder().email("user" + id + "@company.com").name("직원")
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1)).roles(Set.of(role)).build();
        ReflectionTestUtils.setField(e, "id", id);
        return UserPrincipal.from(e);
    }
}
