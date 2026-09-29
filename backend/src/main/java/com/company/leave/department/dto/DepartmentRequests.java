package com.company.leave.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 부서 관련 요청 DTO 모음.
 */
public final class DepartmentRequests {

    private DepartmentRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 100) String name,
            Long parentId,
            Long leadId,
            Integer sortOrder) {
    }

    public record Update(
            @NotBlank @Size(max = 100) String name,
            Long leadId,
            Integer sortOrder) {
    }

    /** 부서 이동: 새 상위 부서(null이면 최상위로) */
    public record Move(Long newParentId) {
    }
}
