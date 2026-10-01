package com.company.leave.department;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("부서 삭제")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DepartmentServiceDeleteTest {

    @Mock private DepartmentRepository departments;
    @Mock private EmployeeRepository employees;
    @Mock private CalendarEventRepository events;

    private DepartmentService service;
    private final Department 옛부서 = new Department("옛부서", null, 0);

    @BeforeEach
    void setUp() {
        service = new DepartmentService(departments, employees, events);
        when(departments.findById(5L)).thenReturn(Optional.of(옛부서));
    }

    @Test
    void 부서를_지우면_그_부서_전용_일정만_지우고_휴가_일정은_건드리지_않는다() {
        when(departments.existsByParentId(5L)).thenReturn(false);
        when(employees.countByDepartmentId(5L)).thenReturn(0L);

        service.delete(5L);

        verify(events).deleteDepartmentEvents(5L);
        verify(events, never()).deleteByLeaveRequestId(anyLong());
        verify(departments).delete(옛부서);
    }

    @Test
    void 직원이_남은_부서는_지우지_않고_일정도_건드리지_않는다() {
        when(departments.existsByParentId(5L)).thenReturn(false);
        when(employees.countByDepartmentId(5L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(BusinessException.class);
        verify(events, never()).deleteDepartmentEvents(anyLong());
        verify(departments, never()).delete(옛부서);
    }
}
