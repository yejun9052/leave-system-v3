package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeaveRequest", description = "휴가 신청/결재")
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceService leaveBalanceService;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
                                 LeaveBalanceService leaveBalanceService) {
        this.leaveRequestService = leaveRequestService;
        this.leaveBalanceService = leaveBalanceService;
    }

    @Operation(summary = "휴가 신청")
    @PostMapping
    public ApiResponse<LeaveRequestDtos.Response> create(
            @Valid @RequestBody LeaveRequestDtos.Create req) {
        return ApiResponse.ok(leaveRequestService.create(SecurityUtils.currentEmployeeId(), req));
    }

    @Operation(summary = "휴가 종류별 신청 가능 여부 · 신청 미리보기",
            description = "정책 사용 여부와 병가·공가 조건(잔여 연차 1일 미만, 대기 중 연차 신청 없음)을 확인하고, "
                    + "승인 시 소멸될 남은 연차를 알려 준다(신청 화면 경고용). "
                    + "startDate·endDate 를 모두 주면 신청과 같은 계산으로 기간 근무일·연차 차감·신청 후 잔여를 "
                    + "미리 보여 주고, 시작일·겹침·잔액 등 규칙 위반이면 allowed=false 와 사유를 준다(저장 안 함).")
    @GetMapping("/eligibility")
    public ApiResponse<LeaveRequestDtos.Eligibility> eligibility(
            @RequestParam Long leaveTypeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Long specialRuleId) {
        return ApiResponse.ok(leaveRequestService.eligibility(
                SecurityUtils.currentEmployeeId(), leaveTypeId, startDate, endDate, hours, specialRuleId));
    }

    @Operation(summary = "내 결재 경로",
            description = "신청하면 누가 먼저 결재하는지(팀장/인사관리자)와, 팀장이 오늘 종일 휴가로 부재라서 "
                    + "인사관리자에게 바로 신청할 수 있는지 알려 준다.")
    @GetMapping("/approval-route")
    public ApiResponse<LeaveRequestDtos.ApprovalRoute> approvalRoute() {
        return ApiResponse.ok(leaveRequestService.approvalRoute(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "내 휴가 신청 목록")
    @GetMapping("/me")
    public ApiResponse<Page<LeaveRequestDtos.Response>> myRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(leaveRequestService.myRequests(
                SecurityUtils.currentEmployeeId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "결재 대기 목록")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @GetMapping("/pending")
    public ApiResponse<List<LeaveRequestDtos.Response>> pending() {
        return ApiResponse.ok(leaveRequestService.pendingForApprover(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 승인")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PostMapping("/{id}/approve")
    public ApiResponse<LeaveRequestDtos.Response> approve(@PathVariable Long id) {
        return ApiResponse.ok(leaveRequestService.approve(id, SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 반려")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PostMapping("/{id}/reject")
    public ApiResponse<LeaveRequestDtos.Response> reject(
            @PathVariable Long id, @Valid @RequestBody LeaveRequestDtos.Reject req) {
        return ApiResponse.ok(
                leaveRequestService.reject(id, SecurityUtils.currentEmployeeId(), req.reason()));
    }

    @Operation(summary = "휴가 취소 (대기건은 즉시 취소, 승인건은 취소 요청)")
    @PostMapping("/{id}/cancel")
    public ApiResponse<LeaveRequestDtos.Response> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) LeaveRequestDtos.CancelRequest req) {
        String reason = req != null ? req.reason() : null;
        return ApiResponse.ok(leaveRequestService.cancel(id, SecurityUtils.currentEmployeeId(), reason));
    }

    @Operation(summary = "휴가 취소 요청 승인 (팀장/관리자)")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PostMapping("/{id}/cancel/approve")
    public ApiResponse<LeaveRequestDtos.Response> approveCancellation(@PathVariable Long id) {
        return ApiResponse.ok(
                leaveRequestService.approveCancellation(id, SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 취소 요청 반려 (팀장/관리자)")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PostMapping("/{id}/cancel/reject")
    public ApiResponse<LeaveRequestDtos.Response> rejectCancellation(
            @PathVariable Long id, @Valid @RequestBody LeaveRequestDtos.Reject req) {
        return ApiResponse.ok(
                leaveRequestService.rejectCancellation(id, SecurityUtils.currentEmployeeId(), req.reason()));
    }

    // --- 잔액 ---

    @Operation(summary = "내 연차 잔액 (연도별)")
    @GetMapping("/balances/me")
    public ApiResponse<LeaveBalanceResponse> myBalance(@RequestParam(required = false) Integer year) {
        int y = year != null ? year : leaveBalanceService.currentYear();
        return ApiResponse.ok(leaveBalanceService.getResponse(SecurityUtils.currentEmployeeId(), y));
    }

    @Operation(summary = "내 연차 잔액 전체 이력")
    @GetMapping("/balances/me/history")
    public ApiResponse<List<LeaveBalanceResponse>> myBalanceHistory() {
        return ApiResponse.ok(leaveBalanceService.listResponses(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "특정 사용자 연차 잔액")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @GetMapping("/balances/{employeeId}")
    public ApiResponse<LeaveBalanceResponse> employeeBalance(
            @PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        // 팀장은 본인 팀원만 조회 가능(IDOR 방지). 관리자는 전체 허용.
        leaveRequestService.assertCanViewEmployeeData(SecurityUtils.currentEmployeeId(), employeeId);
        int y = year != null ? year : leaveBalanceService.currentYear();
        return ApiResponse.ok(leaveBalanceService.getResponse(employeeId, y));
    }
}
