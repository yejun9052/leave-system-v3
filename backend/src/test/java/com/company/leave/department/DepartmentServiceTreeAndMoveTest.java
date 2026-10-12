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
import com.company.leave.employee.domain.Role;
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
        void 부서_인원은_하위_부서까지_합치고_직속_인원은_따로_준다() {
            Department 경영지원팀 = department(1L, "경영지원팀", null);
            Department 일팀 = department(2L, "1팀", 경영지원팀);
            Department 이팀 = department(3L, "2팀", 경영지원팀);
            Department 파트 = department(4L, "1팀 파트", 일팀);
            Department 연구소 = department(5L, "연구소", null);
            when(departments.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(경영지원팀, 일팀, 이팀, 파트, 연구소));
            when(employees.countGroupByDepartment()).thenReturn(List.<Object[]>of(
                    new Object[] {1L, 2L}, new Object[] {2L, 3L}, new Object[] {3L, 4L}, new Object[] {4L, 1L},
                    new Object[] {5L, 6L}));

            List<DepartmentResponse> tree = service.getTree();

            DepartmentResponse 경영 = tree.get(0);
            assertThat(경영.memberCount()).isEqualTo(10L); // 2 + 1팀(3 + 파트 1) + 2팀 4
            assertThat(경영.directMemberCount()).isEqualTo(2L);
            assertThat(경영.children()).extracting(DepartmentResponse::memberCount).containsExactly(4L, 4L);
            assertThat(경영.children().get(0).directMemberCount()).isEqualTo(3L);
            assertThat(tree.get(1).memberCount()).isEqualTo(6L); // 다른 트리에는 더하지 않는다
            assertThat(service.getFlat()).extracting(DepartmentResponse::memberCount).containsExactly(10L, 4L, 4L, 1L, 6L);
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
        void 이름만_고치면_부서장과_정렬순서는_그대로_둔다() {
            Department 개발팀 = new Department("개발팀", null, 3);
            ReflectionTestUtils.setField(개발팀, "id", 2L);
            개발팀.assignLead(employee(20L, "김팀장"));
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));

            DepartmentResponse updated = service.update(2L, new DepartmentRequests.Update("플랫폼팀", null, null));

            assertThat(updated.name()).isEqualTo("플랫폼팀");
            assertThat(updated.leadId()).isEqualTo(20L); // 화면에 남은 옛 값(null)으로 지워지지 않는다
            assertThat(updated.sortOrder()).isEqualTo(3);
        }

        @Test
        void 팀장_권한이_없거나_퇴사한_직원은_부서장으로_지정할_수_없다() {
            Department 개발팀 = new Department("개발팀", null, 3);
            ReflectionTestUtils.setField(개발팀, "id", 2L);
            when(departments.findById(2L)).thenReturn(Optional.of(개발팀));
            Employee 사원 = Employee.builder().email("e@company.com").name("이사원").role(Role.EMPLOYEE).build();
            ReflectionTestUtils.setField(사원, "id", 31L);
            when(employees.findById(31L)).thenReturn(Optional.of(사원));

            assertThatThrownBy(() -> service.update(2L, new DepartmentRequests.Update("개발팀", 31L, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                        assertThat(ex.getMessage()).contains("이사원");
                    });
            assertThat(개발팀.getLead()).isNull();
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
                .passwordHash("hash").hireDate(LocalDate.of(2024, 1, 1)).role(Role.TEAM_LEAD).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
