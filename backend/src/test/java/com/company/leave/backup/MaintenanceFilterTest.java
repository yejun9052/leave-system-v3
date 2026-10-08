package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 점검 모드: 복원 중 API 는 503, 상태 조회는 로그인 없이 바로 답하고, 자동 작업은 건너뛴다. */
@DisplayName("점검 모드")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MaintenanceFilterTest {

    private final MaintenanceMode maintenance = new MaintenanceMode();
    private final MaintenanceFilter filter = new MaintenanceFilter(maintenance, JsonMapper.builder().build());

    private MockHttpServletResponse call(String method, String uri, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, uri), response, chain);
        return response;
    }

    @Test
    void 평소에는_요청을_그대로_통과시킨다() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        assertThat(call("GET", "/api/employees", chain).getStatus()).isEqualTo(200);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void 복원_중에는_API_요청을_503으로_막는다() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        maintenance.begin();

        MockHttpServletResponse response = call("POST", "/api/leave-requests", chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"MAINTENANCE\"").contains("시스템 점검 중입니다");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void 복원_중에도_화면_파일은_보낸다() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        maintenance.begin();

        call("GET", "/assets/index.js", chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void 상태_조회는_세션을_읽지_않고_바로_점검_여부를_답한다() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        assertThat(call("GET", MaintenanceFilter.STATUS_PATH, chain).getContentAsString())
                .contains("\"maintenance\":false");
        maintenance.begin();
        MockHttpServletResponse during = call("GET", MaintenanceFilter.STATUS_PATH, chain);

        assertThat(during.getStatus()).isEqualTo(200);
        assertThat(during.getContentAsString()).contains("\"maintenance\":true");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void 처리_중인_요청이_끝나기를_기다린다() throws Exception {
        AtomicBoolean idleWhileRunning = new AtomicBoolean(true);
        FilterChain chain = (req, res) -> {
            try {
                idleWhileRunning.set(maintenance.awaitIdle(0, Duration.ofMillis(150)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        call("GET", "/api/employees", chain);

        assertThat(idleWhileRunning).isFalse();
        assertThat(maintenance.awaitIdle(0, Duration.ofMillis(10))).isTrue();
    }

    @Test
    void 복원_중에는_예약된_자동_작업을_건너뛴다() throws Throwable {
        MaintenanceSchedulingAspect aspect = new MaintenanceSchedulingAspect(maintenance);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(mock(Signature.class));

        aspect.skipDuringMaintenance(pjp);
        verify(pjp).proceed();

        maintenance.begin();
        ProceedingJoinPoint during = mock(ProceedingJoinPoint.class);
        when(during.getSignature()).thenReturn(mock(Signature.class));
        aspect.skipDuringMaintenance(during);
        verify(during, never()).proceed();
    }
}
