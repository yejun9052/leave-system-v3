package com.company.leave.policy;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.policy.dto.PolicyDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "연차 정책")
@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Operation(summary = "정책 조회")
    @GetMapping
    public ApiResponse<PolicyDtos.Response> get() {
        return ApiResponse.ok(policyService.get());
    }

    @Operation(summary = "정책 수정")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PutMapping
    public ApiResponse<PolicyDtos.Response> update(@Valid @RequestBody PolicyDtos.UpdateRequest req) {
        return ApiResponse.ok(policyService.update(req));
    }
}
