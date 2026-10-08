package com.company.leave.leave.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휴가 신청 응답에 기간별 차감 몫(이번 기간·다음 기간)이 담기는지. 화면은 다음 기간 몫이 있을 때만 나눠 보여 준다.
 */
@DisplayName("휴가 신청 응답의 기간별 차감")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestResponseTest {

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);

    @Test
    void 기산일을_걸친_휴가는_이번_기간과_다음_기간_몫을_나눠_담는다() {
        LeaveRequestDtos.Response r = LeaveRequestDtos.Response.from(휴가("3", "2"));

        assertThat(r.currentPeriodDays()).isEqualByComparingTo("1");
        assertThat(r.nextPeriodDays()).isEqualByComparingTo("2");
    }

    @Test
    void 기산일을_걸치지_않으면_다음_기간_몫은_0이다() {
        LeaveRequestDtos.Response r = LeaveRequestDtos.Response.from(휴가("2", "0"));

        assertThat(r.currentPeriodDays()).isEqualByComparingTo("2");
        assertThat(r.nextPeriodDays()).isEqualByComparingTo("0");
    }

    @Test
    void 결재함용으로_바꿔도_기간별_몫은_그대로다() {
        LeaveRequestDtos.Response r = LeaveRequestDtos.Response.from(휴가("3", "2")).withInbox(false, null)
                .withRequestWarning("안내");

        assertThat(r.currentPeriodDays()).isEqualByComparingTo("1");
        assertThat(r.nextPeriodDays()).isEqualByComparingTo("2");
    }

    private LeaveRequest 휴가(String deducted, String nextPart) {
        Employee 직원 = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(직원, "id", 10L);
        LeaveRequest r = new LeaveRequest(직원, 연차, LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 16),
                new BigDecimal(deducted), new BigDecimal(deducted), 2026, "사유");
        r.assignPeriods(2026, new BigDecimal(nextPart));
        return r;
    }
}
