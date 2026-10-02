package com.company.leave.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.leave.LeaveGrantService;
import com.company.leave.leave.LeavePromotionService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
@DisplayName("연차 자동 부여 스케줄러")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveAccrualSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private LeaveGrantService leaveGrantService;
    @Mock
    private LeavePromotionService promotionService;
    @Mock
    private PolicyService policyService;
    @Mock
    private LeavePolicy policy;

    private LeaveAccrualScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LeaveAccrualScheduler(leaveGrantService, promotionService, policyService);
    }

    private static Scheduled schedule(String methodName) throws NoSuchMethodException {
        return LeaveAccrualScheduler.class.getMethod(methodName).getAnnotation(Scheduled.class);
    }

    private static ZonedDateTime seoul(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, SEOUL);
    }

    private static ZonedDateTime next(String methodName, ZonedDateTime from) throws NoSuchMethodException {
        return CronExpression.parse(schedule(methodName).cron()).next(from);
    }

    @Test
    void 모든_스케줄은_서울_시간대_기준으로_실행된다() throws Exception {
        assertThat(schedule("dailyRecompute").zone()).isEqualTo("Asia/Seoul");
        assertThat(schedule("promotion").zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 재계산은_매일_1시에_실행된다() throws Exception {
        assertThat(next("dailyRecompute", seoul(2026, 3, 5, 0, 59))).isEqualTo(seoul(2026, 3, 5, 1, 0));
        assertThat(next("dailyRecompute", seoul(2026, 3, 5, 1, 0))).isEqualTo(seoul(2026, 3, 6, 1, 0));
    }

    @Test
    void 촉진은_7월_1일과_11월_1일_9시에_실행된다() throws Exception {
        assertThat(next("promotion", seoul(2026, 1, 1, 0, 0))).isEqualTo(seoul(2026, 7, 1, 9, 0));
        assertThat(next("promotion", seoul(2026, 7, 1, 9, 0))).isEqualTo(seoul(2026, 11, 1, 9, 0));
        assertThat(next("promotion", seoul(2026, 11, 1, 9, 0))).isEqualTo(seoul(2027, 7, 1, 9, 0));
    }

    @Test
    void 재계산은_같은_날_공휴일_동기화보다_늦게_실행된다() throws Exception {
        ZonedDateTime newYear = seoul(2027, 1, 1, 0, 0);
        Scheduled holidaySync = HolidaySyncScheduler.class.getMethod("dailySync").getAnnotation(Scheduled.class);

        ZonedDateTime holidaySyncAt = CronExpression.parse(holidaySync.cron()).next(newYear);
        ZonedDateTime recomputeAt = next("dailyRecompute", newYear);

        assertThat(recomputeAt.toLocalDate()).isEqualTo(holidaySyncAt.toLocalDate());
        assertThat(recomputeAt).isAfter(holidaySyncAt);
    }

    @Test
    void 재계산은_직원별_지금_연차_기간_부여를_호출한다() {
        scheduler.dailyRecompute();

        verify(leaveGrantService, times(1)).grantCurrentPeriods();
        verifyNoMoreInteractions(leaveGrantService);
        verifyNoInteractions(promotionService, policyService);
    }

    @Test
    void 촉진_정책이_켜져_있으면_올해_연도와_기준_0일로_촉진을_실행한다() {
        int year = LocalDate.now().getYear();
        when(policyService.getActivePolicy()).thenReturn(policy);
        when(policy.isPromotionEnabled()).thenReturn(true);

        scheduler.promotion();

        verify(promotionService, times(1)).runPromotion(year, BigDecimal.ZERO);
        verifyNoInteractions(leaveGrantService);
    }

    @Test
    void 촉진_정책이_꺼져_있으면_촉진을_실행하지_않는다() {
        when(policyService.getActivePolicy()).thenReturn(policy);
        when(policy.isPromotionEnabled()).thenReturn(false);

        scheduler.promotion();

        verifyNoInteractions(promotionService, leaveGrantService);
    }
}
