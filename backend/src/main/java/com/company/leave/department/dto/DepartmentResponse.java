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
        long memberCount,
        List<DepartmentResponse> children) {

    public static DepartmentResponse of(Department d, String leadName, long memberCount) {
        return new DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getParentId(),
                d.getLeadId(),
                leadName,
                d.getSortOrder(),
                memberCount,
                new ArrayList<>());
    }
}
