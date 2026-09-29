package com.company.leave.license;

import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "License", description = "라이선스 상태")
@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @Operation(summary = "라이선스 상태 조회")
    @GetMapping
    public ApiResponse<LicenseService.LicenseStatus> status() {
        return ApiResponse.ok(licenseService.status());
    }
}
