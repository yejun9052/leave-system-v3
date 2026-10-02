package com.company.leave.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.holiday.HolidaySyncService;
import com.company.leave.leave.LeavePromotionService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정책 → 자동화 탭 정보: 자동 작업 3개(순서·상태·마지막 실행), 촉진 자동 발송 설정과 미리보기, 설정 저장.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("자동화 탭 정보")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AutomationServiceTest {

    @Mock private PolicyService policyService;
    @Mock private LeavePromotionService promotionService;
    @Mock private HolidaySyncService holidaySyncService;
    @Mock private JobRunRecorder recorder;
    @InjectMocks private AutomationService service;

    private final LeavePolicy policy = LeavePolicy.createDefault();

    @BeforeEach
    void setUp() {
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(recorder.lastRuns()).thenReturn(Map.of());
        lenient().when(promotionService.autoPreview()).thenReturn(List.of());
        lenient().when(holidaySyncService.isConfigured()).thenReturn(true);
    }

    @Test
    void 자동_작업은_공휴일_연차_부여_촉진_순서로_실행_시각과_함께_보여준다() {
        AutomationService.Overview o = service.overview();

        assertThat(o.jobs()).extracting(AutomationService.Job::key).containsExactly(
                JobRunRecorder.HOLIDAY_SYNC, JobRunRecorder.LEAVE_GRANT, JobRunRecorder.PROMOTION_AUTO);
        assertThat(o.jobs()).extracting(AutomationService.Job::schedule)
                .containsExactly("매일 00:10", "매일 01:00", "매일 09:00");
        assertThat(o.jobs()).extracting(AutomationService.Job::state).containsExactly(
                AutomationService.State.ALWAYS, AutomationService.State.ALWAYS, AutomationService.State.OFF);
    }

    @Test
    void 공휴일_API_키가_없으면_공휴일_동기화는_설정_없음이다() {
        when(holidaySyncService.isConfigured()).thenReturn(false);

        assertThat(service.overview().jobs().get(0).state()).isEqualTo(AutomationService.State.NOT_CONFIGURED);
    }

    @Test
    void 촉진_자동_발송을_켜면_켜짐이고_발송_시기를_설명에_넣는다() {
        policy.applyAutomation(true, List.of(6, 2));

        AutomationService.Overview o = service.overview();

        assertThat(o.promotionEnabled()).isTrue();
        assertThat(o.promotionMonths()).containsExactly(6, 2);
        AutomationService.Job promotion = o.jobs().get(2);
        assertThat(promotion.state()).isEqualTo(AutomationService.State.ON);
        assertThat(promotion.description()).contains("기한 6개월 전·2개월 전");
    }

    @Test
    void 마지막_실행_결과를_작업별로_붙이고_기록이_없으면_비워_둔다() {
        Instant start = Instant.parse("2026-10-02T16:00:00Z");
        Instant end = Instant.parse("2026-10-02T16:00:03Z");
        when(recorder.lastRuns()).thenReturn(Map.of(JobRunRecorder.LEAVE_GRANT,
                new JobRunRecorder.Run(JobRunRecorder.LEAVE_GRANT, start, end, true, "재직자 20명 부여·재계산")));

        List<AutomationService.Job> jobs = service.overview().jobs();

        AutomationService.Job grant = jobs.get(1);
        assertThat(grant.lastStartedAt()).isEqualTo(start);
        assertThat(grant.lastFinishedAt()).isEqualTo(end);
        assertThat(grant.lastSuccess()).isTrue();
        assertThat(grant.lastMessage()).isEqualTo("재직자 20명 부여·재계산");
        AutomationService.Job holiday = jobs.get(0);
        assertThat(holiday.lastStartedAt()).isNull();
        assertThat(holiday.lastSuccess()).isNull();
        assertThat(holiday.lastMessage()).isNull();
    }

    @Test
    void 촉진_자동_발송_미리보기를_함께_돌려준다() {
        LeavePromotionService.AutoTarget target = new LeavePromotionService.AutoTarget(1L, "홍길동", "개발팀",
                LocalDate.of(2026, 11, 30), "1개월 28일", 2);
        when(promotionService.autoPreview()).thenReturn(List.of(target));

        assertThat(service.overview().promotionPreview()).containsExactly(target);
    }

    @Test
    void 촉진_자동_발송_설정을_저장하고_새_정보를_돌려준다() {
        AutomationService.Overview o = service.updatePromotion(true, List.of(3));

        verify(policyService).updateAutomation(true, List.of(3));
        assertThat(o.jobs()).hasSize(3);
    }

    @Test
    void 발송_시기_설명은_큰_값부터_가운뎃점으로_잇는다() {
        assertThat(AutomationService.monthsLabel(List.of(6, 2))).isEqualTo("기한 6개월 전·2개월 전");
        assertThat(AutomationService.monthsLabel(List.of(1))).isEqualTo("기한 1개월 전");
    }
}
