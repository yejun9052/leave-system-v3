package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeaveAdmin", description = "연차 운영(관리자)")
@RestController
@RequestMapping("/api/leave/admin")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class LeaveAdminController {

    private final LeaveGrantService leaveGrantService;

    public LeaveAdminController(LeaveGrantService leaveGrantService) {
        this.leaveGrantService = leaveGrantService;
    }

    @Operation(summary = "전 직원 연차 부여/재계산 (수동 실행)")
    @PostMapping("/grant")
    public ApiResponse<Map<String, Object>> grantAll(
            @RequestParam(required = false) Integer year) {
        // 연도를 주지 않으면 직원마다 지금 연차 기간(입사일 기준이면 기간이 서로 다르다)
        if (year == null) {
            return ApiResponse.ok(Map.of("granted", leaveGrantService.grantCurrentPeriods()));
        }
        return ApiResponse.ok(Map.of("year", year, "granted", leaveGrantService.grantAll(year)));
    }

    @Operation(summary = "특정 사용자 연차 부여/재계산")
    @PostMapping("/grant/{employeeId}")
    public ApiResponse<Void> grantOne(
            @org.springframework.web.bind.annotation.PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        if (year == null) {
            leaveGrantService.grantCurrentPeriod(employeeId);
        } else {
            leaveGrantService.grantForEmployee(employeeId, year);
        }
        return ApiResponse.ok();
    }
}
