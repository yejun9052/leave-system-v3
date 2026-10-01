package com.company.leave.policy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.time.LocalDate;
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

    private PolicyRulesService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRulesService(awards, specials, blackouts, messenger);
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
