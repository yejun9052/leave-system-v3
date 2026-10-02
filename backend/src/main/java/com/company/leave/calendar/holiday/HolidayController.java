package com.company.leave.calendar.holiday;

import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Holiday", description = "공휴일(관리자)")
@Validated
@RestController
@RequestMapping("/api/holidays")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class HolidayController {

    private final HolidayRepository holidayRepository;
    private final HolidaySyncService syncService;

    public HolidayController(HolidayRepository holidayRepository, HolidaySyncService syncService) {
        this.holidayRepository = holidayRepository;
        this.syncService = syncService;
    }

    public record HolidayResponse(LocalDate date, String name) {
    }

    @Operation(summary = "연도별 공휴일 목록")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<HolidayResponse>> list(@RequestParam @Min(2000) @Max(2100) int year) {
        return ApiResponse.ok(holidayRepository
                .findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(h -> new HolidayResponse(h.getDate(), h.getName()))
                .toList());
    }

    @Operation(summary = "공휴일 수동 동기화",
            description = "공휴일 API 에서 해당 연도를 받아 저장하고, 새 공휴일이 걸친 기존 휴가의 차감 일수를 자동 조정·환원한다.")
    @PostMapping("/sync")
    public ApiResponse<HolidaySyncService.SyncResult> sync(@RequestParam @Min(2000) @Max(2100) int year) {
        try {
            return ApiResponse.ok(syncService.sync(year));
        } catch (HolidayApiException ex) {
            throw new BusinessException(ErrorCode.HOLIDAY_SYNC_FAILED,
                    ErrorCode.HOLIDAY_SYNC_FAILED.defaultMessage() + " (" + ex.getMessage() + ")");
        }
    }
}
