package com.company.leave.employee.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.employee.repository.EmployeeRepositoryImpl.DeptRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("사용자 검색의 부서 조건")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmployeeDepartmentSearchTest {

    // 본사(1) ⊃ 연구소(2) ⊃ QA(3), 개발팀(4) ⊃ 플랫폼파트(5) / 본사 ⊃ 경영지원팀(6)
    private final List<DeptRow> tree = List.of(
            new DeptRow(1L, null, "본사"),
            new DeptRow(2L, 1L, "연구소"),
            new DeptRow(3L, 2L, "QA"),
            new DeptRow(4L, 2L, "개발팀"),
            new DeptRow(5L, 4L, "플랫폼파트"),
            new DeptRow(6L, 1L, "경영지원팀"));

    @Test
    void 상위_부서_이름으로_찾으면_하위_부서_전부가_포함된다() {
        assertThat(EmployeeRepositoryImpl.subtreesMatching(tree, "연구소")).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
    }

    @Test
    void 하위_부서_이름으로_찾으면_그_부서와_그_아래만_포함된다() {
        assertThat(EmployeeRepositoryImpl.subtreesMatching(tree, "개발")).containsExactlyInAnyOrder(4L, 5L);
        assertThat(EmployeeRepositoryImpl.subtreesMatching(tree, "qa")).containsExactly(3L);
    }

    @Test
    void 최상위_부서로_찾으면_전체_부서가_포함되고_없는_이름이면_비어_있다() {
        assertThat(EmployeeRepositoryImpl.subtreesMatching(tree, "본사")).hasSize(6);
        assertThat(EmployeeRepositoryImpl.subtreesMatching(tree, "없는부서")).isEmpty();
    }
}
