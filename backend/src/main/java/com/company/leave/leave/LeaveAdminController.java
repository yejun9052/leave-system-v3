package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeaveAdmin", description = "연차 운영(관리자)")
@RestController
@RequestMapping("/api/leave/admin")
@PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
public class LeaveAdminController {

    private final LeaveGrantService leaveGrantService;

    public LeaveAdminController(LeaveGrantService leaveGrantService) {
        this.leaveGrantService = leaveGrantService;
    }

    @Operation(summary = "전 직원 연차 부여/재계산 (수동 실행)")
    @PostMapping("/grant")
    public ApiResponse<Map<String, Object>> grantAll(
            @RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        int count = leaveGrantService.grantAll(targetYear);
        return ApiResponse.ok(Map.of("year", targetYear, "granted", count));
    }

    @Operation(summary = "특정 사용자 연차 부여/재계산")
    @PostMapping("/grant/{employeeId}")
    public ApiResponse<Void> grantOne(
            @org.springframework.web.bind.annotation.PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        leaveGrantService.grantForEmployee(employeeId, targetYear);
        return ApiResponse.ok();
    }
}
