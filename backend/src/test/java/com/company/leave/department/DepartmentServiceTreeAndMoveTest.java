package com.company.leave.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.dto.DepartmentRequests;
import com.company.leave.department.dto.DepartmentResponse;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("부서 트리 조회·생성·수정·이동")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DepartmentServiceTreeAndMoveTest {

    @Mock private DepartmentRepository departments;
    @Mock private EmployeeRepository employees;
    @Mock private CalendarEventRepository events;

    @InjectMocks private DepartmentService service;

    @Nested
    @DisplayName("트리 조회")
    class 트리_조회 {

        @Test
        void 상위_부서_아래에_하위_부서를_넣고_인원수와_팀장_이름을_붙인다() {
            Department 본사 = department(1L, "본사", null);
            Department 개발팀 = department(2L, "개발팀", 본사);
            개발팀.assignLead(employee(20L, "김팀장"));
            Department 경영지원팀 = department(3L, "경영지원팀", null);
            when(departments.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(본사, 개발팀, 경영지원팀));
            List<Object[]> counts = List.<Object[]>of(new Object[] {2L, 4L});
            when(employees.countGroupByDepartment()).thenReturn(counts);

            List<DepartmentResponse> tree = service.getTree();

            assertThat(tree).extracting(DepartmentResponse::name).containsExactly("본사", "경영지원팀");
            DepartmentResponse 개발 = tree.get(0).children().get(0);
            assertThat(개발.name()).isEqualTo("개발팀");
            assertThat(개발.leadName()).isEqualTo("김팀장");
            assertThat(개발.memberCount()).isEqualTo(4L);
            assertThat(tree.get(1).memberCount()).isZero();
        }

        @Test
        void 상위_부서를_찾을_수_없는_부서는_최상위에_보인다() {
            Department 없는상위 = department(9L, "사라진 상위", null);
            Department 고아 = department(2L, "고아 부서", 없는상위);
            when(departments.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(고아));
            when(employees.countGroupByDepartment()).thenReturn(List.of());

            assertThat(service.getTree()).extracting(DepartmentResponse::name).containsExactly("고아 부서");
        }
    }

    @Nested
    @DisplayName("생성·수정")
    class 생성_수정 {

        @Test
        void 상위_부서와_팀장을_지정해_만들고_정렬순서가_없으면_0으로_둔다() {
            Department 본사 = department(1L, "본사", null);
            when(departments.findById(1L)).thenReturn(Optional.of(본사));
            when(employees.findById(20L)).thenReturn(Optional.of(employee(20L, "김팀장")));
            when(departments.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

            DepartmentResponse created = service.create(new DepartmentRequests.Create("개발팀", 1L, 20L, null));

            assertThat(created.name()).isEqualTo("개발팀");
            assertThat(created.parentId()).isEqualTo(1L);
            assertThat(created.leadName()).isEqualTo("김팀장");
            assertThat(created.sortOrder()).isZero();
        }

        @Test
        void 없는_상위_부서로는_만들_수_없다() {
            when(departments.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(new DepartmentRequests.Create("개발팀", 99L, null, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND));
            verify(departments, never()).save(any());
        }

        @Test
        void 없는_직원을_팀장으로_지정하면_거부된다() {
            when(employees.findById(77L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(new DepartmentRequests.Create("개발팀", null, 77L, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND));
            verify(departments, never()).save(any());
        }

        @Test
        void 수정에서_팀장을_비우면_팀장이_해제되고_정렬순서가_없으면_그대로_둔다() {
            Department 개발팀 = new Department("개발팀", null, 3);
            ReflectionTestUtils.setField(개발팀, "id", 2L);
            개발팀.assignLead(employee(20L, "김팀장"));
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));

            DepartmentResponse updated = service.update(2L, new DepartmentRequests.Update("플랫폼팀", null, null));

            assertThat(updated.name()).isEqualTo("플랫폼팀");
            assertThat(updated.leadId()).isNull();
            assertThat(updated.sortOrder()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("이동")
    class 이동 {

        @Test
        void 다른_부서_아래로_옮긴다() {
            Department 개발팀 = department(2L, "개발팀", null);
            Department 본사 = department(1L, "본사", null);
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));
            when(departments.findById(1L)).thenReturn(Optional.of(본사));
            when(departments.findSubtreeIds(2L)).thenReturn(List.of(2L, 5L));

            DepartmentResponse moved = service.move(2L, new DepartmentRequests.Move(1L));

            assertThat(moved.parentId()).isEqualTo(1L);
        }

        @Test
        void 상위를_비우면_최상위로_뺀다() {
            Department 본사 = department(1L, "본사", null);
            Department 개발팀 = department(2L, "개발팀", 본사);
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));

            DepartmentResponse moved = service.move(2L, new DepartmentRequests.Move(null));

            assertThat(moved.parentId()).isNull();
            verify(departments, never()).findSubtreeIds(any());
        }

        @Test
        void 자기_자신_아래로는_옮길_수_없다() {
            Department 개발팀 = department(2L, "개발팀", null);
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));

            assertThatThrownBy(() -> service.move(2L, new DepartmentRequests.Move(2L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTMENT_CYCLE));
            assertThat(개발팀.getParentId()).isNull();
        }

        @Test
        void 자기_하위_부서_아래로는_옮길_수_없다() {
            Department 개발팀 = department(2L, "개발팀", null);
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));
            when(departments.findSubtreeIds(2L)).thenReturn(List.of(2L, 5L, 6L));

            assertThatThrownBy(() -> service.move(2L, new DepartmentRequests.Move(6L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTMENT_CYCLE));
            assertThat(개발팀.getParentId()).isNull();
            verify(departments, never()).findById(6L);
        }
    }

    private static Department department(Long id, String name, Department parent) {
        Department d = new Department(name, parent, 0);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private static Employee employee(Long id, String name) {
        Employee e = Employee.builder().email("e" + id + "@company.com").name(name)
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1)).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
