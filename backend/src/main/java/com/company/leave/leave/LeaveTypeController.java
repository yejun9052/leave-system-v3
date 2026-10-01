package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.leave.dto.LeaveTypeDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeaveType", description = "휴가 종류")
@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @Operation(summary = "휴가 종류 목록")
    @GetMapping
    public ApiResponse<List<LeaveTypeDtos.Response>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(leaveTypeService.list(includeInactive));
    }

    @Operation(summary = "휴가 종류 생성")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping
    public ApiResponse<LeaveTypeDtos.Response> create(@Valid @RequestBody LeaveTypeDtos.Create req) {
        return ApiResponse.ok(leaveTypeService.create(req));
    }

    @Operation(summary = "휴가 종류 수정")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<LeaveTypeDtos.Response> update(
            @PathVariable Long id, @Valid @RequestBody LeaveTypeDtos.Update req) {
        return ApiResponse.ok(leaveTypeService.update(id, req));
    }

    @Operation(summary = "휴가 종류 삭제")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        leaveTypeService.delete(id);
        return ApiResponse.ok();
    }
}
