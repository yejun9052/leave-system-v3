package com.company.leave.employee.dto;

import com.company.leave.employee.domain.EmployeeStatus;
import java.util.Set;

/**
 * 사용자 검색 조건.
 *
 * @param keyword               이름/이메일 부분일치
 * @param departmentIds         부서 필터: 부서 트리에서 체크한 부서 소속만(체크한 그대로, 하위 부서를 펼치지 않음).
 *                              null 이나 빈 집합이면 조건 없음
 * @param status                재직 상태 필터
 * @param allowedDepartmentIds  조회 허용 부서 제한(권한 스코프). null=제한 없음(관리자),
 *                              빈 집합=조회 대상 없음
 */
public record EmployeeSearchCondition(String keyword, Set<Long> departmentIds, EmployeeStatus status,
                                      Set<Long> allowedDepartmentIds) {

    public EmployeeSearchCondition(String keyword, Set<Long> departmentIds, EmployeeStatus status) {
        this(keyword, departmentIds, status, null);
    }

    public EmployeeSearchCondition withAllowedDepartmentIds(Set<Long> ids) {
        return new EmployeeSearchCondition(keyword, departmentIds, status, ids);
    }
}
