package com.company.leave.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.GrantBasis;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.dto.PolicyDtos;
import com.company.leave.policy.repository.LeavePolicyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 연차 정책 조회·저장: 활성 정책이 없으면 기본 정책을 만들고, 연차 정책 저장은 자동화 설정(촉진 자동 발송)을 건드리지 않으며,
 * 자동화 설정의 잘못된 값은 입력 오류로 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 정책 서비스")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PolicyServiceTest {

    @Mock private LeavePolicyRepository policyRepository;
    @InjectMocks private PolicyService service;

    @Test
    void 활성_정책이_있으면_그대로_돌려준다() {
        LeavePolicy policy = LeavePolicy.createDefault();
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(policy));

        assertThat(service.getActivePolicy()).isSameAs(policy);
        verify(policyRepository, never()).save(any());
    }

    @Test
    void 활성_정책이_없으면_기본_정책을_만들어_저장한다() {
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());
        when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        LeavePolicy policy = service.getActivePolicy();

        verify(policyRepository).save(policy);
        assertThat(policy.getGrantBasis()).isEqualTo(GrantBasis.HIRE_DATE);
        assertThat(policy.getBaseAnnualDays()).isEqualByComparingTo("15");
    }

    @Test
    void 연차_정책을_저장해도_촉진_자동_발송_설정은_바뀌지_않는다() {
        LeavePolicy policy = LeavePolicy.createDefault();
        policy.applyAutomation(true, List.of(3));
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(policy));

        PolicyDtos.Response r = service.update(new PolicyDtos.UpdateRequest(GrantBasis.FISCAL_YEAR, 4, 1,
                new BigDecimal("16"), 2, BigDecimal.ONE, new BigDecimal("25"), true, 11, true, true, true,
                2, 3, 5, false, BigDecimal.ZERO, false));

        assertThat(r.grantBasis()).isEqualTo(GrantBasis.FISCAL_YEAR);
        assertThat(r.baseAnnualDays()).isEqualByComparingTo("16");
        assertThat(r.allowNegative()).isTrue();
        assertThat(r.nextPeriodReservationEnabled()).isFalse();
        assertThat(r.promotionEnabled()).isTrue();
        assertThat(r.promotionMonths()).containsExactly(3);
    }

    @Test
    void 다음_기간_예약_값을_보내지_않으면_켜진_것으로_본다() {
        LeavePolicy policy = LeavePolicy.createDefault();
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(policy));

        PolicyDtos.Response r = service.update(new PolicyDtos.UpdateRequest(GrantBasis.HIRE_DATE, 1, 1,
                new BigDecimal("15"), 2, BigDecimal.ONE, new BigDecimal("25"), true, 11, false, true, false,
                0, 0, 0, false, BigDecimal.ZERO, null));

        assertThat(r.nextPeriodReservationEnabled()).isTrue();
    }

    @Test
    void 금지_기간_처리_방식은_기본이_승인된_휴가만_유지이고_보내면_바꾸며_보내지_않으면_그대로다() {
        LeavePolicy policy = LeavePolicy.createDefault();
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(policy));
        assertThat(policy.getBlackoutConflictMode()).isEqualTo(BlackoutConflictMode.KEEP_APPROVED);

        PolicyDtos.Response changed = service.update(new PolicyDtos.UpdateRequest(GrantBasis.HIRE_DATE, 1, 1,
                new BigDecimal("15"), 2, BigDecimal.ONE, new BigDecimal("25"), true, 11, false, true, false,
                0, 0, 0, false, BigDecimal.ZERO, true, BlackoutConflictMode.CANCEL_ALL));
        PolicyDtos.Response kept = service.update(new PolicyDtos.UpdateRequest(GrantBasis.HIRE_DATE, 1, 1,
                new BigDecimal("15"), 2, BigDecimal.ONE, new BigDecimal("25"), true, 11, false, true, false,
                0, 0, 0, false, BigDecimal.ZERO, true));

        assertThat(changed.blackoutConflictMode()).isEqualTo(BlackoutConflictMode.CANCEL_ALL);
        assertThat(kept.blackoutConflictMode()).isEqualTo(BlackoutConflictMode.CANCEL_ALL);
    }

    @Test
    void 자동화_설정을_저장한다() {
        LeavePolicy policy = LeavePolicy.createDefault();
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(policy));

        service.updateAutomation(true, List.of(2, 6));

        assertThat(policy.isPromotionEnabled()).isTrue();
        assertThat(policy.getPromotionMonths()).containsExactly(6, 2);
    }

    @Test
    void 자동화_설정이_잘못되면_입력_오류로_돌려준다() {
        when(policyRepository.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(LeavePolicy.createDefault()));

        assertThatThrownBy(() -> service.updateAutomation(true, List.of(9)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(ex.getMessage()).contains("1~6개월");
                });
    }
}
