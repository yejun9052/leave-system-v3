package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 신규 사용자 생성 뒤 첫 연차 부여: 지금 연차 기간을 부여하고, 실패해도 사용자 생성에는 영향을 주지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("신규 사용자 첫 연차 부여")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmployeeLeaveInitListenerTest {

    @Mock private LeaveGrantService leaveGrantService;
    @InjectMocks private EmployeeLeaveInitListener listener;

    @Test
    void 사용자가_생기면_그_직원의_지금_연차_기간을_부여한다() {
        listener.onEmployeeCreated(new EmployeeService.EmployeeCreatedEvent(10L));

        verify(leaveGrantService).grantCurrentPeriod(10L);
    }

    @Test
    void 부여에_실패해도_예외를_밖으로_던지지_않는다() {
        when(leaveGrantService.grantCurrentPeriod(10L)).thenThrow(new IllegalStateException("DB 오류"));

        assertThatCode(() -> listener.onEmployeeCreated(new EmployeeService.EmployeeCreatedEvent(10L)))
                .doesNotThrowAnyException();
    }
}
