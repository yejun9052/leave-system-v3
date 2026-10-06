package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("반반차 폐지(시간차로 대체)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveTypeServiceQuarterTest {

    @Mock private LeaveTypeRepository repository;
    @Mock private PolicyService policyService;
    @Mock private SpecialLeaveRuleRepository specialRules;

    private LeaveTypeService service;
    private final LeaveType 기존_반반차 = new LeaveType("QUARTER", "반반차", new BigDecimal("0.25"), true,
            DayPortion.QUARTER, true, false, "#14b8a6", 3);

    @BeforeEach
    void setUp() {
        service = new LeaveTypeService(repository, policyService, specialRules);
        lenient().when(policyService.getActivePolicy()).thenReturn(LeavePolicy.createDefault());
        lenient().when(specialRules.findByLeaveTypeCodeOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
        lenient().when(repository.findById(8L)).thenReturn(Optional.of(기존_반반차));
    }

    @Test
    void 반반차_단위로_새_휴가_종류를_만들_수_없다() {
        assertThatThrownBy(() -> service.create(new LeaveTypeDtos.Create("Q2", "반반차2", new BigDecimal("0.25"),
                true, DayPortion.QUARTER, AnnualDeductionMode.DEDUCT, null, "#000000", 1)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(ex.getMessage()).contains("시간차로 대체");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void 기존_반반차_종류를_다시_켤_수_없다() {
        assertThatThrownBy(() -> service.update(8L, 수정(DayPortion.QUARTER, true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 기존_반반차_종류는_꺼진_채로_이름을_고칠_수_있다() {
        LeaveTypeDtos.Response response = service.update(8L, 수정(DayPortion.QUARTER, false));

        assertThat(response.name()).isEqualTo("반반차(폐지)");
        assertThat(기존_반반차.isActive()).isFalse();
    }

    @Test
    void 시간차_종류는_그대로_만들_수_있다() {
        when(repository.save(any(LeaveType.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeDtos.Response response = service.create(new LeaveTypeDtos.Create("HOURLY2", "시간차2",
                new BigDecimal("0.125"), true, DayPortion.HOURLY, AnnualDeductionMode.DEDUCT, null, "#000000", 1));

        assertThat(response.portion()).isEqualTo(DayPortion.HOURLY);
    }

    private static LeaveTypeDtos.Update 수정(DayPortion portion, boolean active) {
        return new LeaveTypeDtos.Update("반반차(폐지)", new BigDecimal("0.25"), true, portion, AnnualDeductionMode.DEDUCT, null,
                "#14b8a6", 3, active);
    }
}
