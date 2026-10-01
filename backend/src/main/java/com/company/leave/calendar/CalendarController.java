package com.company.leave.calendar;

import com.company.leave.calendar.dto.CalendarDtos;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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

@Tag(name = "Calendar", description = "캘린더")
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(summary = "기간 내 일정 조회")
    @GetMapping("/events")
    public ApiResponse<List<CalendarDtos.EventResponse>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.ok(calendarService.getEvents(start, end, SecurityUtils.currentPrincipal()));
    }

    @Operation(summary = "날짜 상세",
            description = "그날의 휴가 목록(승인 휴가는 모두에게, 결재 대기 휴가는 본인·결재할 수 있는 팀장·인사관리자에게만, "
                    + "사유 제외), 볼 수 있는 등록 일정, 공휴일 이름.")
    @GetMapping("/day")
    public ApiResponse<CalendarDtos.DayDetail> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(calendarService.getDay(date, SecurityUtils.currentPrincipal()));
    }

    @Operation(summary = "일정 등록 범위 선택지",
            description = "관리자는 전체 일정 + 모든 부서, 팀장은 맡은 부서(하위 포함)만. 개인 일정은 제외. "
                    + "저장 시 권한은 등록·수정 API 가 다시 검사한다.")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @GetMapping("/event-scopes")
    public ApiResponse<List<CalendarDtos.EventScopeOption>> eventScopes() {
        return ApiResponse.ok(calendarService.eventScopes(SecurityUtils.currentPrincipal()));
    }

    @Operation(summary = "일정 생성 (관리자/팀장)")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PostMapping("/events")
    public ApiResponse<CalendarDtos.EventResponse> create(
            @Valid @RequestBody CalendarDtos.CreateEvent req) {
        return ApiResponse.ok(calendarService.create(req, SecurityUtils.currentPrincipal()));
    }

    @Operation(summary = "일정 수정")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @PutMapping("/events/{id}")
    public ApiResponse<CalendarDtos.EventResponse> update(
            @PathVariable Long id, @Valid @RequestBody CalendarDtos.CreateEvent req) {
        return ApiResponse.ok(calendarService.update(id, req, SecurityUtils.currentPrincipal()));
    }

    @Operation(summary = "일정 삭제")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','HR_ADMIN','SUPER_ADMIN')")
    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        calendarService.delete(id, SecurityUtils.currentPrincipal());
        return ApiResponse.ok();
    }
}
