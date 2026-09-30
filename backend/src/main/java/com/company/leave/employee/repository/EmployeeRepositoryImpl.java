package com.company.leave.employee.repository;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.QEmployee;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public EmployeeRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Employee> search(EmployeeSearchCondition condition, Pageable pageable) {
        QEmployee e = QEmployee.employee;
        BooleanBuilder where = new BooleanBuilder();
        where.and(e.systemAccount.isFalse()); // 기본 시스템 관리자 계정은 목록에서 제외
        where.and(keyword(e, condition.keyword()));
        if (condition.departmentId() != null) {
            where.and(e.department.id.eq(condition.departmentId()));
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
                .where(where)
                .orderBy(e.name.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(e.count())
                .from(e)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression keyword(QEmployee e, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = "%" + keyword.trim() + "%";
        return e.name.likeIgnoreCase(like)
                .or(e.email.likeIgnoreCase(like));
    }
}
