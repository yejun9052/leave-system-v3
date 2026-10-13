package com.company.leave.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 부서 관련 요청 DTO 모음.
 */
public final class DepartmentRequests {

    private DepartmentRequests() {
    }

    /** 부서장은 받지 않는다: 사용자 관리(팀장 권한·소속 부서)에서만 정해진다. 보내도 무시된다 */
    public record Create(
            @NotBlank @Size(max = 100) String name,
            Long parentId,
            Integer sortOrder) {
    }

    /** 부서장은 받지 않는다(보내도 무시). {@link Create} */
    public record Update(
            @NotBlank @Size(max = 100) String name,
            Integer sortOrder) {
    }

    /** 부서 이동: 새 상위 부서(null이면 최상위로) */
    public record Move(Long newParentId) {
    }
}
