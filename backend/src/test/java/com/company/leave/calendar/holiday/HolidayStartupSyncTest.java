package com.company.leave.calendar.holiday;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
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
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
@DisplayName("기동 시 공휴일 동기화")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidayStartupSyncTest {

    @Mock
    private HolidaySyncService syncService;
    @Mock
    private HolidayRepository holidayRepository;

    private HolidayStartupSync startupSync;
    private final int thisYear = LocalDate.now().getYear();

    @BeforeEach
    void setUp() {
        startupSync = new HolidayStartupSync(syncService, holidayRepository);
    }

    @Test
    void 키가_없으면_공휴일_테이블도_보지_않고_건너뛴다() {
        when(syncService.isConfigured()).thenReturn(false);

        startupSync.run(new DefaultApplicationArguments());

        verifyNoInteractions(holidayRepository);
        verify(syncService, never()).syncCurrentAndNextYear();
    }

    @Test
    void 올해_공휴일이_이미_있으면_동기화하지_않는다() {
        when(syncService.isConfigured()).thenReturn(true);
        when(holidayRepository.findByDateBetweenOrderByDateAsc(
                LocalDate.of(thisYear, 1, 1), LocalDate.of(thisYear, 12, 31)))
                .thenReturn(List.of(new Holiday(LocalDate.of(thisYear, 1, 1), "신정")));

        startupSync.run(new DefaultApplicationArguments());

        verify(syncService, never()).syncCurrentAndNextYear();
    }

    @Test
    void 키가_있고_올해_공휴일이_하나도_없으면_올해와_내년을_동기화한다() {
        when(syncService.isConfigured()).thenReturn(true);
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());

        startupSync.run(new DefaultApplicationArguments());

        verify(holidayRepository).findByDateBetweenOrderByDateAsc(
                LocalDate.of(thisYear, 1, 1), LocalDate.of(thisYear, 12, 31));
        verify(syncService).syncCurrentAndNextYear();
    }
}
