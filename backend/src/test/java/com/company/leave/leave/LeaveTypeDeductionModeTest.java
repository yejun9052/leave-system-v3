package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.company.leave.leave.domain.AnnualDeductionMode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveTypeDtos;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휴가 종류의 연차 차감 방식(DEDUCT 연차처럼 차감 / EXHAUST_FIRST 연차 먼저 소진 / NONE 연차와 무관).
 * 예전 두 스위치(연차에서 차감, 잔여 연차 소진 후 사용)는 선택지 하나로 합쳤고, DB 의 두 열은 새 값에 맞춰 같이 저장한다.
 */
@DisplayName("휴가 종류 차감 방식")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveTypeDeductionModeTest {

    @Nested
    @DisplayName("예전 두 스위치에서 변환")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 변환 {

        @ParameterizedTest(name = "차감 {0}, 소진 후 사용 {1} → {2}")
        @CsvSource({
                "true,  false, DEDUCT",
                "false, true,  EXHAUST_FIRST",
                "false, false, NONE",
                "true,  true,  DEDUCT"   // 모순 상태는 차감 우선(V2 마이그레이션과 같은 규칙)
        })
        void 표대로_바뀐다(boolean deduct, boolean exhausted, AnnualDeductionMode expected) {
            assertThat(AnnualDeductionMode.of(deduct, exhausted)).isEqualTo(expected);
        }

        @Test
        void 예전_스위치로_만든_종류도_같은_규칙을_따른다() {
            assertThat(종류(true, false).getAnnualDeductionMode()).isEqualTo(AnnualDeductionMode.DEDUCT);
            assertThat(종류(false, true).getAnnualDeductionMode()).isEqualTo(AnnualDeductionMode.EXHAUST_FIRST);
            assertThat(종류(false, false).getAnnualDeductionMode()).isEqualTo(AnnualDeductionMode.NONE);
        }
    }

    @Nested
    @DisplayName("차감 방식별 판단과 예전 열")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 판단 {

        @Test
        void 연차처럼_차감은_차감_대상이고_소진_조건은_없다() {
            LeaveType type = 종류(AnnualDeductionMode.DEDUCT);

            assertThat(type.isDeductFromAnnual()).isTrue();
            assertThat(type.isRequiresAnnualExhausted()).isFalse();
            예전_열(type, true, false);
        }

        @Test
        void 연차_먼저_소진은_차감하지_않고_소진_조건이_있다() {
            LeaveType type = 종류(AnnualDeductionMode.EXHAUST_FIRST);

            assertThat(type.isDeductFromAnnual()).isFalse();
            assertThat(type.isRequiresAnnualExhausted()).isTrue();
            예전_열(type, false, true);
        }

        @Test
        void 연차와_무관은_차감도_소진_조건도_없다() {
            LeaveType type = 종류(AnnualDeductionMode.NONE);

            assertThat(type.isDeductFromAnnual()).isFalse();
            assertThat(type.isRequiresAnnualExhausted()).isFalse();
            예전_열(type, false, false);
        }

        @Test
        void 차감_방식을_고치면_예전_열도_같이_바뀐다() {
            LeaveType type = 종류(AnnualDeductionMode.EXHAUST_FIRST);

            type.update("병가", BigDecimal.ONE, false, DayPortion.FULL, AnnualDeductionMode.DEDUCT, "#ef4444", 5, true);

            assertThat(type.getAnnualDeductionMode()).isEqualTo(AnnualDeductionMode.DEDUCT);
            assertThat(type.isDeductFromAnnual()).isTrue();
            예전_열(type, true, false);
        }

        @Test
        void 차감_방식이_비어_있으면_연차처럼_차감으로_본다() {
            assertThat(종류(null).getAnnualDeductionMode()).isEqualTo(AnnualDeductionMode.DEDUCT);
        }

        private void 예전_열(LeaveType type, boolean deductFromAnnual, boolean requiresAnnualExhausted) {
            assertThat(ReflectionTestUtils.getField(type, "deductFromAnnual")).isEqualTo(deductFromAnnual);
            assertThat(ReflectionTestUtils.getField(type, "requiresAnnualExhausted")).isEqualTo(requiresAnnualExhausted);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("관리 화면에서 생성·수정")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 생성_수정 {

        @Mock private LeaveTypeRepository repository;
        @Mock private PolicyService policyService;
        @Mock private SpecialLeaveRuleRepository specialRules;
        @Mock private LeavePolicy policy;

        private LeaveTypeService service() {
            lenient().when(policyService.getActivePolicy()).thenReturn(policy);
            lenient().when(specialRules.findByLeaveTypeCodeOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
            return new LeaveTypeService(repository, policyService, specialRules);
        }

        @Test
        void 병가를_연차처럼_차감으로_바꾸면_차감_대상이_된다() {
            LeaveType 병가 = new LeaveType("SICK", "병가", BigDecimal.ZERO, false, DayPortion.FULL,
                    AnnualDeductionMode.EXHAUST_FIRST, "#ef4444", 5);
            when(repository.findById(6L)).thenReturn(Optional.of(병가));

            LeaveTypeDtos.Response response = service().update(6L, new LeaveTypeDtos.Update("병가", BigDecimal.ONE,
                    false, DayPortion.FULL, AnnualDeductionMode.DEDUCT, null, "#ef4444", 5, true));

            assertThat(response.annualDeductionMode()).isEqualTo(AnnualDeductionMode.DEDUCT);
            assertThat(response.deductFromAnnual()).isTrue();
            assertThat(response.requiresAnnualExhausted()).isFalse();
            assertThat(병가.isDeductFromAnnual()).isTrue();
        }

        @Test
        void 새_종류를_연차와_무관으로_만든다() {
            when(repository.save(any(LeaveType.class))).thenAnswer(inv -> inv.getArgument(0));

            LeaveTypeDtos.Response response = service().create(new LeaveTypeDtos.Create("REFRESH", "리프레시",
                    BigDecimal.ONE, true, DayPortion.FULL, AnnualDeductionMode.NONE, true, "#22c55e", 7));

            assertThat(response.annualDeductionMode()).isEqualTo(AnnualDeductionMode.NONE);
            assertThat(response.deductFromAnnual()).isFalse();
            assertThat(response.requiresAnnualExhausted()).isFalse();
            assertThat(response.allowedDuringBlackout()).isTrue();
        }

        @Test
        void 금지_기간_허용을_안_보내고_만들면_금지_기간에_신청할_수_없다() {
            when(repository.save(any(LeaveType.class))).thenAnswer(inv -> inv.getArgument(0));

            LeaveTypeDtos.Response response = service().create(new LeaveTypeDtos.Create("REFRESH", "리프레시",
                    BigDecimal.ONE, true, DayPortion.FULL, AnnualDeductionMode.NONE, null, "#22c55e", 7));

            assertThat(response.allowedDuringBlackout()).isFalse();
        }

        @Test
        void 차감_방식을_바꿔도_금지_기간_허용은_보내지_않으면_그대로다() {
            LeaveType 공가 = new LeaveType("OFFICIAL", "공가", BigDecimal.ZERO, true, DayPortion.FULL,
                    AnnualDeductionMode.EXHAUST_FIRST, "#8b5cf6", 6).allowDuringBlackout(true);
            when(repository.findById(7L)).thenReturn(Optional.of(공가));

            LeaveTypeDtos.Response response = service().update(7L, new LeaveTypeDtos.Update("공가", BigDecimal.ONE,
                    true, DayPortion.FULL, AnnualDeductionMode.DEDUCT, null, "#8b5cf6", 6, true));

            assertThat(response.annualDeductionMode()).isEqualTo(AnnualDeductionMode.DEDUCT);
            assertThat(response.allowedDuringBlackout()).isTrue();
        }

        @Test
        void 금지_기간_허용을_끄면_꺼진다() {
            LeaveType 공가 = new LeaveType("OFFICIAL", "공가", BigDecimal.ZERO, true, DayPortion.FULL,
                    AnnualDeductionMode.EXHAUST_FIRST, "#8b5cf6", 6).allowDuringBlackout(true);
            when(repository.findById(7L)).thenReturn(Optional.of(공가));

            service().update(7L, new LeaveTypeDtos.Update("공가", BigDecimal.ZERO, true, DayPortion.FULL,
                    AnnualDeductionMode.EXHAUST_FIRST, false, "#8b5cf6", 6, true));

            assertThat(공가.isAllowedDuringBlackout()).isFalse();
        }
    }

    private static LeaveType 종류(boolean deductFromAnnual, boolean requiresAnnualExhausted) {
        return new LeaveType("T", "종류", BigDecimal.ONE, true, DayPortion.FULL, deductFromAnnual,
                requiresAnnualExhausted, "#000000", 1);
    }

    private static LeaveType 종류(AnnualDeductionMode mode) {
        return new LeaveType("T", "종류", BigDecimal.ONE, true, DayPortion.FULL, mode, "#000000", 1);
    }
}
