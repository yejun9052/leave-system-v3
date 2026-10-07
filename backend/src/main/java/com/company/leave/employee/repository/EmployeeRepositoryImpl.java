package com.company.leave.employee.repository;

import com.company.leave.common.search.SearchKeywords;
import com.company.leave.common.search.SearchKeywords.DepartmentNode;
import com.company.leave.department.domain.QDepartment;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.QEmployee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {

    private static final Map<Role, String> ROLE_LABELS = Arrays.stream(Role.values())
            .collect(Collectors.toMap(Function.identity(), Role::label));
    private static final Map<EmployeeStatus, String> STATUS_LABELS = Arrays.stream(EmployeeStatus.values())
            .collect(Collectors.toMap(Function.identity(), EmployeeStatus::label));

    private final JPAQueryFactory queryFactory;

    public EmployeeRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Employee> search(EmployeeSearchCondition condition, Pageable pageable) {
        QEmployee e = QEmployee.employee;
        // 부서 이름 검색에도 부서가 없는 직원이 빠지지 않도록 left join
        QDepartment d = new QDepartment("dept");
        BooleanBuilder where = new BooleanBuilder();
        where.and(keyword(e, d, condition.keyword()));
        if (condition.departmentIds() != null && !condition.departmentIds().isEmpty()) {
            where.and(e.department.id.in(condition.departmentIds()));
        }
        if (condition.allowedDepartmentIds() != null) {
            // 권한 스코프 제한(예: 팀장은 담당 부서만). 빈 집합이면 결과 없음.
            where.and(e.department.id.in(condition.allowedDepartmentIds()));
        }
        if (condition.status() != null) {
            where.and(e.status.eq(condition.status()));
        }

        List<Employee> content = queryFactory
                .selectFrom(e)
                .leftJoin(e.department, d)
                .where(where)
                .orderBy(e.name.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(e.count())
                .from(e)
                .leftJoin(e.department, d)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * 목록 표의 모든 칸으로 검색: 이름·이메일·직급·연락처(부분 일치), 부서(이름이 맞는 부서와 그 하위 부서 전부,
     * 예: "연구소" → 연구소·QA·개발팀 소속), 권한·상태(코드 또는 한글 이름, 예: "팀장", "퇴사").
     * 공백으로 나눈 단어는 모두 맞아야 한다(예: "연구소 팀장").
     */
    private BooleanBuilder keyword(QEmployee e, QDepartment d, String keyword) {
        BooleanBuilder all = new BooleanBuilder();
        List<String> tokens = SearchKeywords.tokens(keyword);
        List<DepartmentNode> departments = tokens.isEmpty() ? List.of() : loadDepartments();
        for (String token : tokens) {
            String like = SearchKeywords.likePattern(token);
            BooleanBuilder any = new BooleanBuilder()
                    .or(e.name.lower().like(like, '\\'))
                    .or(e.email.lower().like(like, '\\'))
                    .or(e.position.lower().like(like, '\\'))
                    .or(e.phone.lower().like(like, '\\'));
            Set<Long> deptIds = SearchKeywords.departmentSubtrees(departments, token);
            if (!deptIds.isEmpty()) {
                any.or(d.id.in(deptIds));
            }
            Set<Role> roles = new java.util.HashSet<>(SearchKeywords.codesMatching(ROLE_LABELS, token));
            Arrays.stream(Role.values()).filter(r -> r.name().toLowerCase().contains(token)).forEach(roles::add);
            if (!roles.isEmpty()) {
                any.or(e.roles.any().in(roles));
            }
            Set<EmployeeStatus> statuses = SearchKeywords.codesMatching(STATUS_LABELS, token);
            if (!statuses.isEmpty()) {
                any.or(e.status.in(statuses));
            }
            all.and(any);
        }
        return all;
    }

    /** 부서 수가 많지 않아 검색 때 한 번 읽어 메모리에서 하위 부서를 펼친다. */
    private List<DepartmentNode> loadDepartments() {
        QDepartment dept = new QDepartment("searchDept");
        QDepartment parent = new QDepartment("searchParent");
        return queryFactory.select(dept.id, parent.id, dept.name)
                .from(dept)
                .leftJoin(dept.parent, parent)
                .fetch().stream()
                .map(t -> new DepartmentNode(t.get(dept.id), t.get(parent.id), t.get(dept.name)))
                .toList();
    }
}
