package com.company.leave.leave;

import com.company.leave.common.search.SearchKeywords;
import com.company.leave.common.search.SearchKeywords.DepartmentNode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * 결재함 "휴가 목록" 검색 조건.
 * <ul>
 *   <li>상태: 고른 상태만(비면 전체)</li>
 *   <li>기간: 휴가 기간이 [from, to] 와 겹치는 것</li>
 *   <li>범위: 팀장은 맡은 부서(하위 포함) 소속 직원만, 인사관리자·시스템 관리자는 전체</li>
 *   <li>검색어: 단어마다 신청자 이름, 부서(상위 부서로 찾으면 하위 포함), 휴가 종류, 경조사 규정, 사유, 결재자, 상태(한글) 중
 *       하나라도 맞으면 되고, 단어끼리는 모두 맞아야 한다</li>
 * </ul>
 */
final class LeaveRequestSearch {

    static final Map<LeaveRequestStatus, String> STATUS_LABELS = Map.of(
            LeaveRequestStatus.PENDING, "결재 대기",
            LeaveRequestStatus.APPROVED, "승인",
            LeaveRequestStatus.REJECTED, "반려",
            LeaveRequestStatus.CANCEL_REQUESTED, "취소 요청",
            LeaveRequestStatus.CANCELLED, "취소");

    private LeaveRequestSearch() {
    }

    /**
     * @param departmentScope 팀장이 볼 수 있는 부서 id(인사관리자·시스템 관리자는 null = 전체)
     * @param departments     검색어의 부서 조건용 전체 부서(검색어가 없으면 빈 목록이어도 된다)
     */
    static Specification<LeaveRequest> of(String keyword, Collection<LeaveRequestStatus> statuses, LocalDate from,
                                          LocalDate to, Collection<Long> departmentScope,
                                          List<DepartmentNode> departments) {
        List<String> tokens = SearchKeywords.tokens(keyword);
        return (root, query, cb) -> {
            Join<LeaveRequest, Employee> employee = root.join("employee", JoinType.INNER);
            List<Predicate> all = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                all.add(root.get("status").in(statuses));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            if (to != null) {
                all.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            }
            if (departmentScope != null) {
                all.add(employee.get("department").get("id").in(departmentScope));
            }
            if (!tokens.isEmpty()) {
                Join<LeaveRequest, LeaveType> type = root.join("leaveType", JoinType.INNER);
                Join<LeaveRequest, Employee> approver = root.join("approver", JoinType.LEFT);
                for (String token : tokens) {
                    all.add(cb.or(anyColumn(cb, root, employee, type, approver, departments, token)
                            .toArray(Predicate[]::new)));
                }
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static List<Predicate> anyColumn(CriteriaBuilder cb, Root<LeaveRequest> root,
                                             Join<LeaveRequest, Employee> employee, Join<LeaveRequest, LeaveType> type,
                                             Join<LeaveRequest, Employee> approver, List<DepartmentNode> departments,
                                             String token) {
        String like = SearchKeywords.likePattern(token);
        List<Predicate> or = new ArrayList<>();
        or.add(cb.like(cb.lower(employee.get("name")), like, '\\'));
        or.add(cb.like(cb.lower(type.get("name")), like, '\\'));
        or.add(cb.like(cb.lower(root.get("specialRuleName")), like, '\\'));
        or.add(cb.like(cb.lower(root.get("reason")), like, '\\'));
        or.add(cb.like(cb.lower(approver.get("name")), like, '\\'));
        Set<Long> deptIds = SearchKeywords.departmentSubtrees(departments, token);
        if (!deptIds.isEmpty()) {
            or.add(employee.get("department").get("id").in(deptIds));
        }
        Set<LeaveRequestStatus> statuses = SearchKeywords.codesMatching(STATUS_LABELS, token);
        if (!statuses.isEmpty()) {
            or.add(root.get("status").in(statuses));
        }
        return or;
    }
}
