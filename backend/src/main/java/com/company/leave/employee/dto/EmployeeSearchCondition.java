package com.company.leave.employee.dto;

import com.company.leave.employee.domain.EmployeeStatus;
import java.util.Set;

/**
 * 사용자 검색 조건.
 *
 * @param keyword               이름/이메일 부분일치
 * @param departmentId          부서 필터 (해당 부서 직속)
 * @param status                재직 상태 필터
 * @param allowedDepartmentIds  조회 허용 부서 제한(권한 스코프). null=제한 없음(관리자),
 *                              빈 집합=조회 대상 없음
 */
public record EmployeeSearchCondition(String keyword, Long departmentId, EmployeeStatus status,
                                      Set<Long> allowedDepartmentIds) {

    public EmployeeSearchCondition(String keyword, Long departmentId, EmployeeStatus status) {
        this(keyword, departmentId, status, null);
    }

    public EmployeeSearchCondition withAllowedDepartmentIds(Set<Long> ids) {
        return new EmployeeSearchCondition(keyword, departmentId, status, ids);
    }
}
