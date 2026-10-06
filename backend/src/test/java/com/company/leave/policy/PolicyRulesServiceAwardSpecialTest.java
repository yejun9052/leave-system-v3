package com.company.leave.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 장기근속 포상휴가·경조사 규정 관리(블랙아웃은 PolicyRulesServiceBlackoutTest). */
@ExtendWith(MockitoExtension.class)
@DisplayName("포상휴가·경조사 규정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PolicyRulesServiceAwardSpecialTest {

    @Mock private ServiceAwardRuleRepository awards;
    @Mock private SpecialLeaveRuleRepository specials;
    @Mock private BlackoutPeriodRepository blackouts;
    @Mock private AnnouncementMessenger messenger;

    private PolicyRulesService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRulesService(awards, specials, blackouts, messenger);
    }

    @Nested
    @DisplayName("장기근속 포상휴가")
    class 포상휴가 {

        @Test
        void 등록되지_않은_근속연수면_규칙을_저장한다() {
            when(awards.findByYears(5)).thenReturn(Optional.empty());
            when(awards.save(any(ServiceAwardRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PolicyRuleDtos.AwardRule created =
                    service.createAward(new PolicyRuleDtos.AwardRuleRequest(5, new BigDecimal("3"), "5년 근속 포상"));

            assertThat(created.years()).isEqualTo(5);
            assertThat(created.bonusDays()).isEqualByComparingTo("3");
            assertThat(created.name()).isEqualTo("5년 근속 포상");
        }

        @Test
        void 이미_있는_근속연수는_다시_등록할_수_없다() {
            when(awards.findByYears(5)).thenReturn(Optional.of(new ServiceAwardRule(5, new BigDecimal("3"), null)));

            assertThatThrownBy(() -> service.createAward(
                    new PolicyRuleDtos.AwardRuleRequest(5, new BigDecimal("5"), "중복")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
            verify(awards, never()).save(any());
        }

        @Test
        void 규칙을_고치면_근속연수와_일수와_명칭이_바뀐다() {
            ServiceAwardRule rule = new ServiceAwardRule(5, new BigDecimal("3"), "5년 근속 포상");
            when(awards.findById(7L)).thenReturn(Optional.of(rule));

            PolicyRuleDtos.AwardRule updated =
                    service.updateAward(7L, new PolicyRuleDtos.AwardRuleRequest(10, new BigDecimal("5"), "10년 근속 포상"));

            assertThat(updated.years()).isEqualTo(10);
            assertThat(updated.bonusDays()).isEqualByComparingTo("5");
            assertThat(updated.name()).isEqualTo("10년 근속 포상");
        }

        @Test
        void 없는_규칙은_고칠_수_없다() {
            when(awards.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAward(99L,
                    new PolicyRuleDtos.AwardRuleRequest(10, new BigDecimal("5"), null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        void 삭제하면_그_규칙을_지운다() {
            service.deleteAward(7L);

            verify(awards).deleteById(7L);
        }
    }

    @Nested
    @DisplayName("경조사 규정")
    class 경조사 {

        @Test
        void 일수는_반일이나_하루_단위만_받는다() {
            when(specials.save(any(SpecialLeaveRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.createSpecial(new PolicyRuleDtos.SpecialRuleRequest("생일", new BigDecimal("0.5"),
                    "CONDOLENCE", null)).days()).isEqualByComparingTo("0.5");
            assertThat(service.createSpecial(new PolicyRuleDtos.SpecialRuleRequest("본인 결혼", new BigDecimal("5.0"),
                    "CONDOLENCE", null)).days()).isEqualByComparingTo("5");
            for (String days : new String[] {"0.25", "1.5", "0", "-1"}) {
                assertThatThrownBy(() -> service.createSpecial(new PolicyRuleDtos.SpecialRuleRequest("잘못", new BigDecimal(days),
                        "CONDOLENCE", null)))
                        .as(days)
                        .isInstanceOfSatisfying(BusinessException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                            assertThat(ex.getMessage()).contains("0.5일(반차) 또는 1일 단위");
                        });
            }
        }

        @Test
        void 고칠_때도_일수는_반일이나_하루_단위만_받는다() {
            SpecialLeaveRule rule = new SpecialLeaveRule("생일", new BigDecimal("0.5"), "CONDOLENCE", 1);
            when(specials.findById(3L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.updateSpecial(3L,
                    new PolicyRuleDtos.SpecialRuleRequest("생일", new BigDecimal("0.25"), "CONDOLENCE", 1)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
            assertThat(rule.getDays()).isEqualByComparingTo("0.5");
        }

        @Test
        void 정렬순서를_주지_않으면_0으로_만든다() {
            when(specials.save(any(SpecialLeaveRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PolicyRuleDtos.SpecialRule created = service.createSpecial(
                    new PolicyRuleDtos.SpecialRuleRequest("본인 결혼", new BigDecimal("5"), "CONDOLENCE", null));

            assertThat(created.name()).isEqualTo("본인 결혼");
            assertThat(created.days()).isEqualByComparingTo("5");
            assertThat(created.leaveTypeCode()).isEqualTo("CONDOLENCE");
            assertThat(created.sortOrder()).isZero();
        }

        @Test
        void 규정을_고치면_이름과_일수와_휴가_종류와_정렬순서가_바뀐다() {
            SpecialLeaveRule rule = new SpecialLeaveRule("본인 결혼", new BigDecimal("5"), "CONDOLENCE", 0);
            when(specials.findById(3L)).thenReturn(Optional.of(rule));

            PolicyRuleDtos.SpecialRule updated = service.updateSpecial(3L,
                    new PolicyRuleDtos.SpecialRuleRequest("자녀 결혼", new BigDecimal("1"), "CONDOLENCE", 2));

            assertThat(updated.name()).isEqualTo("자녀 결혼");
            assertThat(updated.days()).isEqualByComparingTo("1");
            assertThat(updated.sortOrder()).isEqualTo(2);
        }

        @Test
        void 없는_규정은_고칠_수_없다() {
            when(specials.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateSpecial(99L,
                    new PolicyRuleDtos.SpecialRuleRequest("자녀 결혼", new BigDecimal("1"), null, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        void 삭제하면_그_규정을_지운다() {
            service.deleteSpecial(3L);

            verify(specials).deleteById(3L);
        }
    }
}
