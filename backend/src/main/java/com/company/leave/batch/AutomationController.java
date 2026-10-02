package com.company.leave.batch;

import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 정책 → 자동화 탭. 이벤트 로그에는 "정책" 변경으로 남는다(/api/policy). */
@Tag(name = "Automation", description = "자동 작업·연차 촉진 자동 발송")
@RestController
@RequestMapping("/api/policy/automation")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    /** @param promotionMonths 발송 시기: 사용 기한 몇 개월 전(1~6, 하나 이상) */
    public record PromotionRequest(@NotNull Boolean promotionEnabled, @NotNull List<Integer> promotionMonths) {
    }

    @Operation(summary = "자동 작업 목록·마지막 실행 결과, 연차 촉진 자동 발송 설정과 지금 기준 발송 예정 대상")
    @GetMapping
    public ApiResponse<AutomationService.Overview> overview() {
        return ApiResponse.ok(automationService.overview());
    }

    @Operation(summary = "연차 촉진 자동 발송 ON/OFF·발송 시기 저장")
    @PutMapping("/promotion")
    public ApiResponse<AutomationService.Overview> updatePromotion(@Valid @RequestBody PromotionRequest req) {
        return ApiResponse.ok(automationService.updatePromotion(req.promotionEnabled(), req.promotionMonths()));
    }
}
