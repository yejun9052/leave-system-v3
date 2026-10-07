package com.company.leave.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "리포트")
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class ReportController {

    private final LeaveReportService reportService;

    public ReportController(LeaveReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "연차 사용 현황 엑셀",
            description = "부서(departmentIds)·사용자(employeeIds)를 고르면 그 대상만(둘을 합침), 아무것도 고르지 않으면 전체.")
    @GetMapping("/leave-usage/export")
    public ResponseEntity<ByteArrayResource> exportUsage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) List<Long> departmentIds,
            @RequestParam(required = false) List<Long> employeeIds) {
        int y = year != null ? year : LocalDate.now().getYear();
        byte[] bytes = reportService.exportUsage(y,
                departmentIds != null ? departmentIds : List.of(),
                employeeIds != null ? employeeIds : List.of());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"leave-usage-" + y + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(bytes));
    }
}
