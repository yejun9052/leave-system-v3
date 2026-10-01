package com.company.leave.audit;

import com.company.leave.audit.dto.AuditLogResponse;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit", description = "이벤트(감사) 로그")
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "이벤트 로그 목록 (검색/페이지)")
    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(PageResponse.from(auditService.search(keyword, PageRequest.of(page, size))));
    }
}
