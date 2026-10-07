package com.company.leave.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.LeaveRequestService.DateRange;
import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("블랙아웃 추가·변경·삭제 공지")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PolicyRulesServiceBlackoutTest {

    private static final LocalDate D1 = LocalDate.of(2026, 12, 28);
    private static final LocalDate D2 = LocalDate.of(2026, 12, 31);

    @Mock private ServiceAwardRuleRepository awards;
    @Mock private SpecialLeaveRuleRepository specials;
    @Mock private BlackoutPeriodRepository blackouts;
    @Mock private AnnouncementMessenger messenger;
    @Mock private LeaveRequestService leaveRequestService;
    @Mock private PolicyService policyService;
    private final LeavePolicy policy = LeavePolicy.createDefault();

    private PolicyRulesService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRulesService(awards, specials, blackouts, messenger, leaveRequestService, policyService);
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
    }

    // --- 겹치는 기존 휴가 처리 ---

    @Test
    void 추가하면_금지_기간_전체와_겹치는_휴가를_지금_정책대로_처리한다() {
        when(blackouts.save(any(BlackoutPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        policy.changeBlackoutConflictMode(BlackoutConflictMode.CANCEL_ALL);

        service.createBlackout(new PolicyRuleDtos.BlackoutRequest(D1, D2, "결산 주간"), 7L);

        verify(leaveRequestService).applyBlackout("결산 주간", D1, D2, List.of(new DateRange(D1, D2)),
                BlackoutConflictMode.CANCEL_ALL, 7L);
    }

    @Test
    void 늘려_수정하면_새로_늘어난_날짜만_처리한다() {
        when(blackouts.findById(5L)).thenReturn(Optional.of(new BlackoutPeriod(D1, D1, "결산")));

        service.updateBlackout(5L, new PolicyRuleDtos.BlackoutRequest(D1, D2, "결산 주간"), 7L);

        verify(leaveRequestService).applyBlackout("결산 주간", D1, D2, List.of(new DateRange(D1.plusDays(1), D2)),
                BlackoutConflictMode.KEEP_APPROVED, 7L);
    }

    @Test
    void 이름만_바꾸거나_기간을_줄이면_휴가를_처리하지_않는다() {
        when(blackouts.findById(5L)).thenReturn(Optional.of(new BlackoutPeriod(D1, D2, "결산")));

        service.updateBlackout(5L, new PolicyRuleDtos.BlackoutRequest(D1, D2, "결산 주간"), 7L);
        service.updateBlackout(5L, new PolicyRuleDtos.BlackoutRequest(D1.plusDays(1), D2, "결산 주간"), 7L);

        verify(leaveRequestService, never()).applyBlackout(any(), any(), any(), anyList(), any(), any());
    }

    @Test
    void 수정도_종료일이_시작일보다_빠르면_거부한다() {
        when(blackouts.findById(5L)).thenReturn(Optional.of(new BlackoutPeriod(D1, D2, "결산")));

        assertThatThrownBy(() -> service.updateBlackout(5L, new PolicyRuleDtos.BlackoutRequest(D2, D1, "결산"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(messenger, never()).blackout(any(), any(), any(), any());
    }

    @Test
    void 새로_늘어난_날짜는_원래_기간_앞뒤로_나뉘고_겹치지_않으면_새_기간_전체다() {
        LocalDate d = LocalDate.of(2026, 10, 19);
        assertThat(PolicyRulesService.addedDates(d, d.plusDays(4), d.minusDays(2), d.plusDays(6))).containsExactly(
                new DateRange(d.minusDays(2), d.minusDays(1)), new DateRange(d.plusDays(5), d.plusDays(6)));
        assertThat(PolicyRulesService.addedDates(d, d.plusDays(4), d, d.plusDays(4))).isEmpty();
        assertThat(PolicyRulesService.addedDates(d, d.plusDays(4), d.plusDays(10), d.plusDays(11)))
                .containsExactly(new DateRange(d.plusDays(10), d.plusDays(11)));
    }

    @Test
    void 미리보기는_등록이면_전체를_늘려_수정이면_늘어난_날짜만_보고_늘렸는지_알려_준다() {
        when(blackouts.findById(5L)).thenReturn(Optional.of(new BlackoutPeriod(D1, D1, "결산")));
        LeaveRequestService.BlackoutPlan empty = new LeaveRequestService.BlackoutPlan(List.of(), List.of(), List.of());
        when(leaveRequestService.planBlackout(any(), any(), anyList(), any())).thenReturn(empty);

        PolicyRuleDtos.BlackoutImpact create = service.blackoutImpact(D1, D2, null);
        PolicyRuleDtos.BlackoutImpact extend = service.blackoutImpact(D1, D2, 5L);

        assertThat(create.extended()).isFalse();
        assertThat(extend.extended()).isTrue();
        assertThat(create.mode()).isEqualTo(BlackoutConflictMode.KEEP_APPROVED);
        verify(leaveRequestService).planBlackout(D1, D2, List.of(new DateRange(D1, D2)), BlackoutConflictMode.KEEP_APPROVED);
        verify(leaveRequestService).planBlackout(D1, D2, List.of(new DateRange(D1.plusDays(1), D2)),
                BlackoutConflictMode.KEEP_APPROVED);
    }

    @Test
    void 추가하면_추가_공지를_보낸다() {
        when(blackouts.save(any(BlackoutPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createBlackout(new PolicyRuleDtos.BlackoutRequest(D1, D2, "결산 주간"), 7L);

        verify(messenger).blackout(Change.CREATED, new Schedule("결산 주간", D1, D2, null), null, 7L);
    }

    @Test
    void 변경하면_변경_전_내용과_함께_공지를_보낸다() {
        when(blackouts.findById(5L)).thenReturn(Optional.of(new BlackoutPeriod(D1, D1, "결산")));

        service.updateBlackout(5L, new PolicyRuleDtos.BlackoutRequest(D1, D2, "결산 주간"), 7L);

        verify(messenger).blackout(Change.UPDATED, new Schedule("결산 주간", D1, D2, null),
                new Schedule("결산", D1, D1, null), 7L);
    }

    @Test
    void 삭제하면_지운_기간으로_삭제_공지를_보내고_없는_기간이면_아무것도_하지_않는다() {
        BlackoutPeriod period = new BlackoutPeriod(D1, D2, "결산 주간");
        when(blackouts.findById(5L)).thenReturn(Optional.of(period));
        when(blackouts.findById(6L)).thenReturn(Optional.empty());

        service.deleteBlackout(5L, 7L);
        service.deleteBlackout(6L, 7L);

        verify(blackouts).delete(period);
        verify(messenger).blackout(Change.DELETED, new Schedule("결산 주간", D1, D2, null), null, 7L);
        verifyNoMoreInteractions(messenger);
    }
}
