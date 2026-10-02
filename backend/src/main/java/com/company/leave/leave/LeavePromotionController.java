package com.company.leave.leave;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LeavePromotion", description = "연차 사용 촉진")
@RestController
@RequestMapping("/api/leave/promotion")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class LeavePromotionController {

    private final LeavePromotionService promotionService;

    public LeavePromotionController(LeavePromotionService promotionService) {
        this.promotionService = promotionService;
    }

    public record SendRequest(@NotEmpty @Size(max = 1000) List<Long> employeeIds) {
    }

    @Operation(summary = "촉진 대상 목록",
            description = "사용 기한(직원별 연차 기간의 마지막 날)이 오늘부터 months 개월(1~6) 안이고 "
                    + "남은 연차가 있는 재직자. 사용 기한이 가까운 순. keyword 는 이름·부서 검색"
                    + "(공백으로 나눈 단어 모두, 상위 부서로 찾으면 하위 부서 포함).")
    @GetMapping("/targets")
    public ApiResponse<List<LeavePromotionService.Target>> targets(
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(promotionService.targets(months, keyword));
    }

    @Operation(summary = "고른 직원에게 촉진 안내 발송",
            description = "앱 알림과 메일(남은 기간은 서버가 계산)을 보내고 발송 이력을 남긴다. "
                    + "대상이 아니게 된 직원(그 사이 휴가 신청 등)은 건너뛴다.")
    @PostMapping("/send")
    public ApiResponse<LeavePromotionService.SendResult> send(@Valid @RequestBody SendRequest req) {
        return ApiResponse.ok(promotionService.send(req.employeeIds(), SecurityUtils.currentEmployeeId()));
    }
}
