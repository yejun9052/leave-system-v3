package com.company.leave.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.CalendarEventScope;
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
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("캘린더 일정 등록 범위")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CalendarServiceEventScopesTest {

    @Mock private CalendarEventRepository events;
    @Mock private HolidayRepository holidays;
    @Mock private DepartmentRepository departments;
    @Mock private LeaveRequestService leaveRequests;

    private CalendarService service;

    // 트리: 제품개발팀(정렬 0) ─ 서비스파트·플랫폼파트(정렬 0), 경영지원팀(정렬 1)
    private Department product;
    private Department service2;
    private Department platform;
    private Department support;

    @BeforeEach
    void setUp() {
        service = new CalendarService(events, holidays, departments, leaveRequests);
        product = department(2L, "제품개발팀", null, 0);
        platform = department(3L, "플랫폼파트", product, 0);
        service2 = department(4L, "서비스파트", product, 0);
        support = department(5L, "경영지원팀", null, 1);
        // 저장소는 정렬순서·이름순의 평평한 목록을 준다(저장 검사 테스트는 목록을 쓰지 않아 lenient)
        lenient().when(departments.findAllByOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(service2, product, platform, support));
    }

    @Test
    void 관리자는_전체_일정과_모든_부서를_부서_관리_트리_순서로_받는다() {
        List<CalendarDtos.EventScopeOption> options = service.eventScopes(user(10L, Role.HR_ADMIN));

        assertThat(options)
                .extracting(CalendarDtos.EventScopeOption::scope, CalendarDtos.EventScopeOption::departmentId,
                        CalendarDtos.EventScopeOption::label)
                .containsExactly(
                        tuple(CalendarEventScope.COMPANY, null, "전체 일정"),
                        tuple(CalendarEventScope.DEPARTMENT, 2L, "제품개발팀 일정"),
                        tuple(CalendarEventScope.DEPARTMENT, 4L, "서비스파트 일정"),
                        tuple(CalendarEventScope.DEPARTMENT, 3L, "플랫폼파트 일정"),
                        tuple(CalendarEventScope.DEPARTMENT, 5L, "경영지원팀 일정"));
        verify(departments, never()).findByLeadId(any());
    }

    @Test
    void 시스템_관리자도_관리자와_같은_목록을_받는다() {
        List<CalendarDtos.EventScopeOption> options = service.eventScopes(user(1L, Role.SYSTEM_ADMIN));

        assertThat(options).hasSize(5);
        assertThat(options.get(0).scope()).isEqualTo(CalendarEventScope.COMPANY);
    }

    @Test
    void 팀장은_전체_일정_없이_맡은_부서와_그_하위_부서만_받는다() {
        when(departments.findByLeadId(20L)).thenReturn(List.of(product));
        when(departments.findSubtreeIds(2L)).thenReturn(List.of(2L, 3L, 4L));

        List<CalendarDtos.EventScopeOption> options = service.eventScopes(user(20L, Role.TEAM_LEAD));

        assertThat(options)
                .extracting(CalendarDtos.EventScopeOption::departmentId, CalendarDtos.EventScopeOption::label)
                .containsExactly(tuple(2L, "제품개발팀 일정"), tuple(4L, "서비스파트 일정"), tuple(3L, "플랫폼파트 일정"));
        assertThat(options).allMatch(o -> o.scope() == CalendarEventScope.DEPARTMENT);
    }

    @Test
    void 맡은_부서가_없는_팀장은_빈_목록을_받는다() {
        when(departments.findByLeadId(21L)).thenReturn(List.of());

        assertThat(service.eventScopes(user(21L, Role.TEAM_LEAD))).isEmpty();
    }

    @Test
    void 팀장이_맡지_않은_부서로_일정을_저장하면_거부된다() {
        when(departments.findByLeadId(20L)).thenReturn(List.of(product));
        when(departments.findSubtreeIds(2L)).thenReturn(List.of(2L, 3L, 4L));

        assertThatThrownBy(() -> service.create(event(CalendarEventScope.DEPARTMENT, 5L), user(20L, Role.TEAM_LEAD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(events, never()).save(any());
    }

    @Test
    void 부서를_고르지_않은_부서_일정은_수정할_수_없다() {
        assertThatThrownBy(() -> service.update(1L, event(CalendarEventScope.DEPARTMENT, null), user(10L, Role.HR_ADMIN)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(events, never()).findById(any());
    }

    private CalendarDtos.CreateEvent event(CalendarEventScope scope, Long departmentId) {
        LocalDate day = LocalDate.of(2026, 10, 14);
        return new CalendarDtos.CreateEvent("워크숍", day, day, true, scope, departmentId, null);
    }

    private Department department(Long id, String name, Department parent, int sortOrder) {
        Department d = new Department(name, parent, sortOrder);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private UserPrincipal user(Long id, Role role) {
        Employee e = Employee.builder().email("user" + id + "@company.com").name("직원")
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1))
                .systemAccount(role == Role.SYSTEM_ADMIN).roles(Set.of(role)).build();
        ReflectionTestUtils.setField(e, "id", id);
        return UserPrincipal.from(e);
    }
}
