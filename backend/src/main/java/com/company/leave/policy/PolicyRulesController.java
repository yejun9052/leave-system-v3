package com.company.leave.policy;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.policy.dto.PolicyRuleDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PolicyRules", description = "정책 규칙 (포상/경조사/블랙아웃)")
@RestController
@RequestMapping("/api/policy")
public class PolicyRulesController {

    private final PolicyRulesService service;

    public PolicyRulesController(PolicyRulesService service) {
        this.service = service;
    }

    private static final String MANAGER = "hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')";

    // 장기근속 포상
    @GetMapping("/award-rules")
    public ApiResponse<List<PolicyRuleDtos.AwardRule>> awards() {
        return ApiResponse.ok(service.listAwards());
    }

    @PreAuthorize(MANAGER)
    @PostMapping("/award-rules")
    public ApiResponse<PolicyRuleDtos.AwardRule> createAward(
            @Valid @RequestBody PolicyRuleDtos.AwardRuleRequest req) {
        return ApiResponse.ok(service.createAward(req));
    }

    @PreAuthorize(MANAGER)
    @PutMapping("/award-rules/{id}")
    public ApiResponse<PolicyRuleDtos.AwardRule> updateAward(
            @PathVariable Long id, @Valid @RequestBody PolicyRuleDtos.AwardRuleRequest req) {
        return ApiResponse.ok(service.updateAward(id, req));
    }

    @PreAuthorize(MANAGER)
    @DeleteMapping("/award-rules/{id}")
    public ApiResponse<Void> deleteAward(@PathVariable Long id) {
        service.deleteAward(id);
        return ApiResponse.ok();
    }

    // 경조사
    @GetMapping("/special-rules")
    public ApiResponse<List<PolicyRuleDtos.SpecialRule>> specials() {
        return ApiResponse.ok(service.listSpecials());
    }

    @PreAuthorize(MANAGER)
    @PostMapping("/special-rules")
    public ApiResponse<PolicyRuleDtos.SpecialRule> createSpecial(
            @Valid @RequestBody PolicyRuleDtos.SpecialRuleRequest req) {
        return ApiResponse.ok(service.createSpecial(req));
    }

    @PreAuthorize(MANAGER)
    @PutMapping("/special-rules/{id}")
    public ApiResponse<PolicyRuleDtos.SpecialRule> updateSpecial(
            @PathVariable Long id, @Valid @RequestBody PolicyRuleDtos.SpecialRuleRequest req) {
        return ApiResponse.ok(service.updateSpecial(id, req));
    }

    @PreAuthorize(MANAGER)
    @DeleteMapping("/special-rules/{id}")
    public ApiResponse<Void> deleteSpecial(@PathVariable Long id) {
        service.deleteSpecial(id);
        return ApiResponse.ok();
    }

    // 블랙아웃
    @GetMapping("/blackouts")
    public ApiResponse<List<PolicyRuleDtos.Blackout>> blackouts() {
        return ApiResponse.ok(service.listBlackouts());
    }

    @PreAuthorize(MANAGER)
    @PostMapping("/blackouts")
    public ApiResponse<PolicyRuleDtos.Blackout> createBlackout(
            @Valid @RequestBody PolicyRuleDtos.BlackoutRequest req) {
        return ApiResponse.ok(service.createBlackout(req));
    }

    @PreAuthorize(MANAGER)
    @PutMapping("/blackouts/{id}")
    public ApiResponse<PolicyRuleDtos.Blackout> updateBlackout(
            @PathVariable Long id, @Valid @RequestBody PolicyRuleDtos.BlackoutRequest req) {
        return ApiResponse.ok(service.updateBlackout(id, req));
    }

    @PreAuthorize(MANAGER)
    @DeleteMapping("/blackouts/{id}")
    public ApiResponse<Void> deleteBlackout(@PathVariable Long id) {
        service.deleteBlackout(id);
        return ApiResponse.ok();
    }
}
