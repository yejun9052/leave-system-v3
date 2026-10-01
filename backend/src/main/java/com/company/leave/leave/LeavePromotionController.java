package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeavePromotion", description = "연차 촉진 / 미사용 현황")
@RestController
@RequestMapping("/api/leave/promotion")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class LeavePromotionController {

    private final LeavePromotionService promotionService;

    public LeavePromotionController(LeavePromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "미사용 연차(촉진 대상) 목록")
    @GetMapping("/targets")
    public ApiResponse<List<LeavePromotionService.Target>> targets(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") BigDecimal threshold) {
        int y = year != null ? year : LocalDate.now().getYear();
        return ApiResponse.ok(promotionService.targets(y, threshold));
    }

    @Operation(summary = "연차 촉진 알림 발송")
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> run(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") BigDecimal threshold) {
        int y = year != null ? year : LocalDate.now().getYear();
        int count = promotionService.runPromotion(y, threshold);
        return ApiResponse.ok(Map.of("year", y, "notified", count));
    }
}
