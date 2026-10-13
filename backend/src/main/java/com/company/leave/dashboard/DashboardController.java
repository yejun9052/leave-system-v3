package com.company.leave.dashboard;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.dashboard.dto.DashboardDtos;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "개인 대시보드")
    @GetMapping("/me")
    public ApiResponse<DashboardDtos.PersonalDashboard> me() {
        return ApiResponse.ok(dashboardService.personal(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "관리자 대시보드")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/admin")
    public ApiResponse<DashboardDtos.AdminDashboard> admin() {
        return ApiResponse.ok(dashboardService.admin());
    }

    @Operation(summary = "직원별 연차 사용 (리포트 › 회사 현황)",
            description = "재직 중인 직원마다 지금 연차 기간의 부여·이월·사용·남은 연차와 사용률")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/admin/employees")
    public ApiResponse<List<DashboardDtos.EmployeeUsage>> employeeUsage() {
        return ApiResponse.ok(dashboardService.employeeUsage());
    }
}
