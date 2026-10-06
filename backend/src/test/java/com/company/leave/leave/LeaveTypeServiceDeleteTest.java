package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 휴가 종류 삭제. 서비스는 있는 종류인지만 확인하고 지운다.
 * 신청 이력이 있는 종류를 막는 것은 DB 참조 제약에 맡겨져 있어 단위 테스트로는 확인하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 종류 삭제")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveTypeServiceDeleteTest {

    @Mock private LeaveTypeRepository repository;
    @Mock private PolicyService policyService;
    @Mock private SpecialLeaveRuleRepository specialRules;

    private LeaveTypeService service;

    @BeforeEach
    void setUp() {
        service = new LeaveTypeService(repository, policyService, specialRules);
    }

    @Test
    void 있는_종류는_지운다() {
        LeaveType 공가 = new LeaveType("OFFICIAL", "공가", BigDecimal.ONE, true, DayPortion.FULL,
                false, false, "#64748b", 9);
        when(repository.findById(4L)).thenReturn(Optional.of(공가));

        service.delete(4L);

        verify(repository).delete(공가);
    }

    @Test
    void 없는_종류는_지울_수_없다() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_TYPE_NOT_FOUND));
        verify(repository, never()).delete(any());
    }
}
