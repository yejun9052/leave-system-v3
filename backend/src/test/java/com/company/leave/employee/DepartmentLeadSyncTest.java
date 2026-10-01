package com.company.leave.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
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
@DisplayName("부서장 자동 지정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DepartmentLeadSyncTest {

    @Mock private DepartmentRepository departments;
    @Mock private EmployeeRepository employees;

    private DepartmentLeadSync sync;
    private final List<Employee> all = new ArrayList<>();
    private Department 일팀;
    private Department 이팀;

    @BeforeEach
    void setUp() {
        sync = new DepartmentLeadSync(departments, employees);
        일팀 = 부서(30L, "1팀");
        이팀 = 부서(31L, "2팀");
        lenient().when(departments.findByLeadId(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return List.of(일팀, 이팀).stream()
                    .filter(d -> d.getLead() != null && d.getLead().getId().equals(id)).toList();
        });
        lenient().when(employees.findByDepartmentId(anyLong())).thenAnswer(inv -> {
            Long deptId = inv.getArgument(0);
            return all.stream().filter(e -> deptId.equals(e.getDepartmentId())).toList();
        });
    }

    @Test
    void 팀장_권한이_있으면_부서장이_빈_자기_부서의_부서장이_된다() {
        Employee 박서연 = 직원(7L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);

        sync.afterSave(박서연, null, false);

        assertThat(일팀.getLead()).isSameAs(박서연);
    }

    @Test
    void 부서장이_이미_있으면_바꾸지_않는다() {
        Employee 박서연 = 직원(7L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        일팀.assignLead(박서연);
        Employee 강현우 = 직원(9L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);

        sync.afterSave(강현우, null, false);

        assertThat(일팀.getLead()).isSameAs(박서연);
    }

    @Test
    void 팀장_권한이_빠지면_부서장을_다른_팀장으로_바꾼다() {
        Employee 박서연 = 직원(7L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        Employee 강현우 = 직원(9L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        일팀.assignLead(박서연);

        박서연.replaceRoles(Set.of(Role.EMPLOYEE));
        sync.afterSave(박서연, 일팀.getId(), true);

        assertThat(일팀.getLead()).isSameAs(강현우);
    }

    @Test
    void 다른_부서로_옮기면_떠난_부서는_비우고_새_부서가_비어_있으면_부서장이_된다() {
        Employee 박서연 = 직원(7L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        일팀.assignLead(박서연);

        박서연.assignDepartment(이팀);
        sync.afterSave(박서연, 일팀.getId(), true);

        assertThat(일팀.getLead()).isNull();
        assertThat(이팀.getLead()).isSameAs(박서연);
    }

    @Test
    void 퇴사하면_맡던_부서를_다른_팀장에게_넘기고_없으면_비운다() {
        Employee 박서연 = 직원(7L, 일팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        일팀.assignLead(박서연);

        박서연.resign(LocalDate.of(2026, 10, 1));
        sync.afterSave(박서연, 일팀.getId(), true);

        assertThat(일팀.getLead()).isNull();
    }

    @Test
    void 팀장_권한이_없는_사람은_부서장으로_지정하지_않는다() {
        Employee 사원 = 직원(13L, 일팀, Role.EMPLOYEE);

        sync.afterSave(사원, null, false);

        assertThat(일팀.getLead()).isNull();
    }

    @Test
    void 부서_관리에서_직접_지정한_다른_부서의_부서장은_이름_수정으로_풀리지_않는다() {
        Employee 인사과장 = 직원(2L, 이팀, Role.EMPLOYEE, Role.TEAM_LEAD, Role.HR_ADMIN);
        일팀.assignLead(인사과장); // 소속은 2팀이지만 1팀 부서장으로 직접 지정

        sync.afterSave(인사과장, 이팀.getId(), true); // 부서·권한 변화 없는 저장

        assertThat(일팀.getLead()).isSameAs(인사과장);
    }

    private Department 부서(Long id, String name) {
        Department d = new Department(name, null, 0);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private Employee 직원(Long id, Department department, Role... roles) {
        Employee e = Employee.builder().email("e" + id + "@company.com").name("직원" + id).passwordHash("h")
                .hireDate(LocalDate.of(2024, 1, 1)).department(department).roles(Set.of(roles)).build();
        ReflectionTestUtils.setField(e, "id", id);
        all.add(e);
        return e;
    }
}
