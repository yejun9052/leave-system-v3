package com.company.leave.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
import java.util.function.Supplier;
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
    @Mock
    private JobRunRecorder recorder;

    private LeaveAccrualScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LeaveAccrualScheduler(leaveGrantService, promotionService, policyService, recorder);
        기록은_작업만_실행();
    }

    /** 실행 기록은 DB 대신 작업만 그대로 실행한다. */
    @SuppressWarnings("unchecked")
    private void 기록은_작업만_실행() {
        lenient().doAnswer(inv -> ((Supplier<String>) inv.getArgument(1)).get())
                .when(recorder).run(anyString(), any());
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
    void 촉진_자동_발송은_매일_9시에_실행된다() throws Exception {
        assertThat(next("promotion", seoul(2026, 3, 5, 8, 59))).isEqualTo(seoul(2026, 3, 5, 9, 0));
        assertThat(next("promotion", seoul(2026, 3, 5, 9, 0))).isEqualTo(seoul(2026, 3, 6, 9, 0));
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
        verify(recorder).run(eq(JobRunRecorder.LEAVE_GRANT), any());
        verifyNoMoreInteractions(leaveGrantService);
        verifyNoInteractions(promotionService, policyService);
    }

    @Test
    void 촉진_자동_발송이_켜져_있으면_자동_발송을_실행하고_기록한다() {
        when(policyService.getActivePolicy()).thenReturn(policy);
        when(policy.isPromotionEnabled()).thenReturn(true);
        when(promotionService.autoSend()).thenReturn(new LeavePromotionService.AutoResult(2, 1, 3));

        scheduler.promotion();

        verify(promotionService, times(1)).autoSend();
        verify(recorder).run(eq(JobRunRecorder.PROMOTION_AUTO), any());
        verifyNoInteractions(leaveGrantService);
    }

    @Test
    void 촉진_자동_발송이_꺼져_있으면_보내지_않고_건너뜀으로_기록한다() {
        when(policyService.getActivePolicy()).thenReturn(policy);
        when(policy.isPromotionEnabled()).thenReturn(false);

        scheduler.promotion();

        verifyNoInteractions(promotionService, leaveGrantService);
        verify(recorder).skipped(JobRunRecorder.PROMOTION_AUTO, "자동 발송이 꺼져 있어 보내지 않음");
    }
}
