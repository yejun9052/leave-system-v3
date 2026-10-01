package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.dto.PageResponse;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

    @Operation(summary = "휴가 강제 등록 (인사관리자·시스템 관리자)",
            description = "다른 직원의 휴가를 바로 승인 상태로 등록한다. 지난 날짜도 가능, 시작일은 근무일(주말·공휴일 불가). "
                    + "블랙아웃·사전 신청 등 사용 통제는 적용하지 않고 겹침·잔액·경조사 규정은 확인한다.")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/register")
    public ApiResponse<LeaveRequestDtos.Response> register(@Valid @RequestBody LeaveRequestDtos.Register req) {
        return ApiResponse.ok(leaveRequestService.register(SecurityUtils.currentEmployeeId(), req));
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
            description = "결재자 종류(LEAD 결재 팀장 / HR 인사관리자 / SELF 자가 승인 가능)와 결재 팀장 이름. "
                    + "어느 경우든 인사관리자도 결재할 수 있다.")
    @GetMapping("/approval-route")
    public ApiResponse<LeaveRequestDtos.ApprovalRoute> approvalRoute() {
        return ApiResponse.ok(leaveRequestService.approvalRoute(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "내 휴가 신청 목록")
    @GetMapping("/me")
    public ApiResponse<PageResponse<LeaveRequestDtos.Response>> myRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(leaveRequestService.myRequests(
                SecurityUtils.currentEmployeeId(), PageRequest.of(page, size))));
    }

    @Operation(summary = "결재 대기 목록")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/pending")
    public ApiResponse<List<LeaveRequestDtos.Response>> pending() {
        return ApiResponse.ok(leaveRequestService.pendingForApprover(SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 승인")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/{id}/approve")
    public ApiResponse<LeaveRequestDtos.Response> approve(@PathVariable Long id) {
        return ApiResponse.ok(leaveRequestService.approve(id, SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 반려")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/{id}/reject")
    public ApiResponse<LeaveRequestDtos.Response> reject(
            @PathVariable Long id, @Valid @RequestBody LeaveRequestDtos.Reject req) {
        return ApiResponse.ok(
                leaveRequestService.reject(id, SecurityUtils.currentEmployeeId(), req.reason()));
    }

    @Operation(summary = "휴가 취소",
            description = "본인: 대기 건은 즉시 취소, 승인 건은 시작 전까지 취소 요청. "
                    + "인사관리자·시스템 관리자: 즉시 취소, 다른 직원의 승인 휴가나 이미 시작된 휴가는 강제 취소(사유 필수).")
    @PostMapping("/{id}/cancel")
    public ApiResponse<LeaveRequestDtos.Response> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) LeaveRequestDtos.CancelRequest req) {
        String reason = req != null ? req.reason() : null;
        return ApiResponse.ok(leaveRequestService.cancel(id, SecurityUtils.currentEmployeeId(), reason));
    }

    @Operation(summary = "휴가 취소 요청 승인 (결재자: 담당 팀장·인사관리자·시스템 관리자)")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/{id}/cancel/approve")
    public ApiResponse<LeaveRequestDtos.Response> approveCancellation(@PathVariable Long id) {
        return ApiResponse.ok(
                leaveRequestService.approveCancellation(id, SecurityUtils.currentEmployeeId()));
    }

    @Operation(summary = "휴가 취소 요청 반려 (결재자: 담당 팀장·인사관리자·시스템 관리자)")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
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
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/balances/{employeeId}")
    public ApiResponse<LeaveBalanceResponse> employeeBalance(
            @PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        // 팀장은 본인 팀원만 조회 가능(IDOR 방지). 관리자는 전체 허용.
        leaveRequestService.assertCanViewEmployeeData(SecurityUtils.currentEmployeeId(), employeeId);
        int y = year != null ? year : leaveBalanceService.currentYear();
        return ApiResponse.ok(leaveBalanceService.getResponse(employeeId, y));
    }
}
