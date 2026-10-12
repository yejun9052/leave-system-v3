package com.company.leave.department.dto;

import com.company.leave.department.domain.Department;
import java.util.ArrayList;
import java.util.List;

/**
 * 부서 트리 노드 응답.
 */
public record DepartmentResponse(
        Long id,
        String name,
        Long parentId,
        Long leadId,
        String leadName,
        int sortOrder,
        /** 인원: 하위 부서까지 합친 수(예: 경영지원팀 = 직속 + 1팀 + 2팀) */
        long memberCount,
        /** 직속 인원(이 부서에 바로 속한 사람만) */
        long directMemberCount,
        List<DepartmentResponse> children) {

    public static DepartmentResponse of(Department d, String leadName, long memberCount, long directMemberCount) {
        return new DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getParentId(),
                d.getLeadId(),
                leadName,
                d.getSortOrder(),
                memberCount,
                directMemberCount,
                new ArrayList<>());
    }
}
