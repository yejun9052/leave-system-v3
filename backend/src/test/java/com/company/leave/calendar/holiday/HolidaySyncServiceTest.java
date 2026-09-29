package com.company.leave.calendar.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.leave.HolidayImpactService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("공휴일 동기화")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidaySyncServiceTest {

    private static final int YEAR = 2025;

    @Mock
    private HolidayApiClient apiClient;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private HolidayImpactService impactService;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** holidays 테이블 대역(저장한 행이 다음 조회에 보이도록). */
    private final List<Holiday> table = new ArrayList<>();
    private HolidaySyncService service;

    @BeforeEach
    void setUp() {
        service = new HolidaySyncService(apiClient, holidayRepository, impactService, transactionManager);
        lenient().when(apiClient.isConfigured()).thenReturn(true);
        lenient().when(apiClient.fetchMonth(eq(YEAR), anyInt())).thenReturn(List.of());
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenAnswer(inv -> List.copyOf(table));
        lenient().when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> {
            table.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(impactService.applyNewHolidays(anyMap()))
                .thenReturn(new HolidayImpactService.ImpactSummary(0, BigDecimal.ZERO));
    }

    @Test
    void 같은_날짜의_공휴일_두_개는_이름을_합쳐_한_행으로_저장한다() {
        when(apiClient.fetchMonth(YEAR, 5)).thenReturn(List.of(
                항목(LocalDate.of(2025, 5, 5), "어린이날"),
                항목(LocalDate.of(2025, 5, 5), "부처님오신날")));

        HolidaySyncService.SyncResult result = service.sync(YEAR);

        assertThat(table).singleElement().satisfies(h -> {
            assertThat(h.getDate()).isEqualTo(LocalDate.of(2025, 5, 5));
            assertThat(h.getName()).isEqualTo("어린이날, 부처님오신날");
        });
        assertThat(result.added()).hasSize(1);
    }

    @Test
    void 열두_달_중_한_달이라도_실패하면_DB를_건드리지_않는다() {
        when(apiClient.fetchMonth(YEAR, 1)).thenReturn(List.of(항목(LocalDate.of(2025, 1, 1), "신정")));
        when(apiClient.fetchMonth(YEAR, 7)).thenThrow(new HolidayApiException("7월 호출 실패"));

        assertThatThrownBy(() -> service.sync(YEAR)).isInstanceOf(HolidayApiException.class);

        verify(holidayRepository, never()).save(any());
        verifyNoInteractions(impactService, transactionManager);
    }

    @Test
    void 두_번_연속_실행하면_두_번째는_추가가_없고_휴가_재계산_대상도_없다() {
        when(apiClient.fetchMonth(YEAR, 3)).thenReturn(List.of(
                항목(LocalDate.of(2025, 3, 1), "삼일절"), 항목(LocalDate.of(2025, 3, 3), "대체공휴일(삼일절)")));

        HolidaySyncService.SyncResult first = service.sync(YEAR);
        HolidaySyncService.SyncResult second = service.sync(YEAR);

        assertThat(first.added()).hasSize(2);
        assertThat(second.added()).isEmpty();
        assertThat(table).hasSize(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<LocalDate, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(impactService, org.mockito.Mockito.times(2)).applyNewHolidays(captor.capture());
        assertThat(captor.getAllValues().get(1)).isEmpty();
    }

    @Test
    void 새로_추가된_날짜만_휴가_재계산에_넘긴다() {
        table.add(new Holiday(LocalDate.of(2025, 1, 1), "신정"));
        when(apiClient.fetchMonth(YEAR, 1)).thenReturn(List.of(항목(LocalDate.of(2025, 1, 1), "신정")));
        when(apiClient.fetchMonth(YEAR, 6)).thenReturn(List.of(항목(LocalDate.of(2025, 6, 3), "임시공휴일")));

        service.sync(YEAR);

        verify(impactService).applyNewHolidays(Map.of(LocalDate.of(2025, 6, 3), "임시공휴일"));
    }

    @Test
    void 이름이_바뀐_날짜는_이름만_갱신한다() {
        table.add(new Holiday(LocalDate.of(2025, 3, 3), "삼일절 대체공휴일"));
        when(apiClient.fetchMonth(YEAR, 3)).thenReturn(List.of(항목(LocalDate.of(2025, 3, 3), "대체공휴일(삼일절)")));

        HolidaySyncService.SyncResult result = service.sync(YEAR);

        assertThat(table).singleElement().extracting(Holiday::getName).isEqualTo("대체공휴일(삼일절)");
        assertThat(result.renamed()).singleElement()
                .extracting(HolidaySyncService.Renamed::before).isEqualTo("삼일절 대체공휴일");
        assertThat(result.added()).isEmpty();
    }

    @Test
    void API_응답에_없는_기존_날짜는_지우지_않고_결과에만_알린다() {
        table.add(new Holiday(LocalDate.of(2025, 12, 31), "회사 창립기념일"));

        HolidaySyncService.SyncResult result = service.sync(YEAR);

        assertThat(table).hasSize(1);
        assertThat(result.missingInApi()).extracting(HolidaySyncService.NamedDate::name)
                .containsExactly("회사 창립기념일");
    }

    @Test
    void 공휴일이_아닌_특일과_다른_해_날짜는_저장하지_않는다() {
        when(apiClient.fetchMonth(YEAR, 7)).thenReturn(List.of(
                new HolidayApiClient.HolidayItem(LocalDate.of(2025, 7, 17), "제헌절", false),
                항목(LocalDate.of(2026, 1, 1), "다른 해")));

        service.sync(YEAR);

        assertThat(table).isEmpty();
    }

    @Test
    void 키가_없으면_API를_부르지_않고_예외를_던진다() {
        when(apiClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.sync(YEAR)).isInstanceOf(HolidayApiException.class);

        verify(apiClient, never()).fetchMonth(anyInt(), anyInt());
    }

    private static HolidayApiClient.HolidayItem 항목(LocalDate date, String name) {
        return new HolidayApiClient.HolidayItem(date, name, true);
    }
}
