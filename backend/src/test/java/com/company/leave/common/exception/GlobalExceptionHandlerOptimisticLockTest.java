package com.company.leave.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@DisplayName("동시 처리 충돌 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 같은_휴가_신청을_동시에_처리하면_늦은_쪽은_이미_처리된_신청이라는_409를_받는다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(LeaveRequest.class, 10L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().message()).contains("이미 처리된 신청입니다");
    }

    @Test
    void 잔액_등_다른_충돌은_다시_시도하라는_409를_받는다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(LeaveBalance.class, 3L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().message()).contains("잠시 후 다시 시도");
    }
}
