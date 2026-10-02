package com.company.leave.calendar.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
@DisplayName("공휴일 관리자 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidayControllerTest {

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private HolidaySyncService syncService;

    private HolidayController controller;

    @BeforeEach
    void setUp() {
        controller = new HolidayController(holidayRepository, syncService);
    }

    @Test
    void 연도별_목록은_그해_1월_1일부터_12월_31일까지_날짜와_이름을_돌려준다() {
        when(holidayRepository.findByDateBetweenOrderByDateAsc(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)))
                .thenReturn(List.of(new Holiday(LocalDate.of(2027, 1, 1), "1월1일"),
                        new Holiday(LocalDate.of(2027, 5, 3), "대체공휴일(노동절)")));

        ApiResponse<List<HolidayController.HolidayResponse>> response = controller.list(2027);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(
                new HolidayController.HolidayResponse(LocalDate.of(2027, 1, 1), "1월1일"),
                new HolidayController.HolidayResponse(LocalDate.of(2027, 5, 3), "대체공휴일(노동절)"));
    }

    @Test
    void 수동_동기화는_동기화_결과를_그대로_돌려준다() {
        HolidaySyncService.SyncResult result = new HolidaySyncService.SyncResult(2027,
                List.of(new HolidaySyncService.NamedDate(LocalDate.of(2027, 5, 3), "대체공휴일(노동절)")),
                List.of(), List.of(), 1, BigDecimal.ONE);
        when(syncService.sync(2027)).thenReturn(result);

        assertThat(controller.sync(2027).data()).isSameAs(result);
    }

    @Test
    void 공휴일_API가_실패하면_동기화_실패_오류와_원인을_함께_알린다() {
        when(syncService.sync(2027)).thenThrow(new HolidayApiException("공휴일 API 오류 resultCode=22"));

        assertThatThrownBy(() -> controller.sync(2027))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HOLIDAY_SYNC_FAILED);
                    assertThat(ex.getMessage()).contains("resultCode=22");
                });
    }

    @Test
    void 인사_관리자와_최고_관리자만_호출할_수_있다() {
        PreAuthorize rule = HolidayController.class.getAnnotation(PreAuthorize.class);

        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')");
    }
}
