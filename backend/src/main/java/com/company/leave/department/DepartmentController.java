package com.company.leave.department;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.department.dto.DepartmentRequests;
import com.company.leave.department.dto.DepartmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Department", description = "부서 관리")
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @Operation(summary = "부서 조회 (tree 또는 flat)")
    @GetMapping
    public ApiResponse<List<DepartmentResponse>> list(
            @RequestParam(defaultValue = "tree") String view) {
        return ApiResponse.ok("flat".equalsIgnoreCase(view)
                ? departmentService.getFlat()
                : departmentService.getTree());
    }

    @Operation(summary = "부서 생성")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody DepartmentRequests.Create req) {
        return ApiResponse.ok(departmentService.create(req));
    }

    @Operation(summary = "부서 수정")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<DepartmentResponse> update(
            @PathVariable Long id, @Valid @RequestBody DepartmentRequests.Update req) {
        return ApiResponse.ok(departmentService.update(id, req));
    }

    @Operation(summary = "부서 상·하위 이동")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PatchMapping("/{id}/move")
    public ApiResponse<DepartmentResponse> move(
            @PathVariable Long id, @Valid @RequestBody DepartmentRequests.Move req) {
        return ApiResponse.ok(departmentService.move(id, req));
    }

    @Operation(summary = "부서 삭제")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.ok();
    }
}
