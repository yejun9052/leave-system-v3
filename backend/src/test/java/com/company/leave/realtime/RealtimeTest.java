package com.company.leave.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 갱신(SSE): 데이터를 바꾸는 API 가 성공하면 연결된 화면에 "바뀜" 신호를 보낸다.
 * 신호에는 데이터 없이 바꾼 탭 ID 만 담는다. DB는 기동하지 않는다.
 */
@DisplayName("실시간 갱신")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RealtimeTest {

    private static final long 사원 = 7L;
    private static final long 팀장 = 8L;

    /** 실제 API 대신 쓰는 컨트롤러: 경로만 같다 */
    @RestController
    static class FakeApi {
        @PostMapping("/api/leave-requests/1/approve")
        String approve() {
            return "ok";
        }

        @PostMapping("/api/notifications/read-all")
        String readAll() {
            return "ok";
        }

        @PostMapping("/api/leave-requests/2/approve")
        String fail() {
            throw new BusinessException(ErrorCode.LEAVE_ALREADY_STARTED);
        }

        @GetMapping("/api/dashboard")
        String dashboard() {
            return "ok";
        }
    }

    private RealtimeHub hub;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        hub = new RealtimeHub();
        mvc = MockMvcBuilders.standaloneSetup(new RealtimeController(hub), new FakeApi())
                .addInterceptors(new RealtimeChangeInterceptor(hub))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        hub.shutdown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void 연결하면_바로_connected_를_보내고_다시_연결_간격을_알려준다() throws Exception {
        MvcResult stream = 연결(사원);

        기다림(() -> 내용(stream).contains("event:connected"));
        assertThat(내용(stream)).contains("retry:3000");
        assertThat(stream.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(hub.connectionCount()).isEqualTo(1);
    }

    @Test
    void 휴가를_승인하면_모두에게_바꾼_탭_ID_를_담아_신호를_보낸다() throws Exception {
        MvcResult mine = 연결(사원);
        MvcResult lead = 연결(팀장);

        로그인(팀장);
        mvc.perform(post("/api/leave-requests/1/approve").header("X-Client-Id", "tab-1"));

        기다림(() -> 내용(mine).contains("event:change") && 내용(lead).contains("event:change"));
        assertThat(내용(mine)).contains("data:{\"origin\":\"tab-1\"}");
        assertThat(내용(lead)).contains("data:{\"origin\":\"tab-1\"}");
    }

    @Test
    void 알림_읽음처럼_나만_보는_데이터는_내_연결에만_보낸다() throws Exception {
        MvcResult mine = 연결(사원);
        MvcResult lead = 연결(팀장);

        로그인(사원);
        mvc.perform(post("/api/notifications/read-all"));
        // 그다음 모두에게 가는 신호로 순서를 맞춘다: 팀장은 이 신호 하나만 받아야 한다
        mvc.perform(post("/api/leave-requests/1/approve"));

        기다림(() -> 내용(lead).contains("event:change"));
        assertThat(신호_수(mine)).isEqualTo(2);
        assertThat(신호_수(lead)).isEqualTo(1);
    }

    @Test
    void 실패한_요청과_조회는_신호를_보내지_않는다() throws Exception {
        MvcResult mine = 연결(사원);

        로그인(사원);
        mvc.perform(post("/api/leave-requests/2/approve"));
        mvc.perform(get("/api/dashboard"));
        mvc.perform(post("/api/leave-requests/1/approve"));

        기다림(() -> 내용(mine).contains("event:change"));
        assertThat(신호_수(mine)).isEqualTo(1);
    }

    @Test
    void 서버를_끌_때는_열린_연결을_먼저_모두_닫는다() throws Exception {
        MvcResult mine = 연결(사원);
        기다림(() -> 내용(mine).contains("event:connected"));
        hub.start();

        hub.stop();

        assertThat(hub.isRunning()).isFalse();
        assertThat(hub.connectionCount()).isZero();
    }

    @Test
    void 자동_작업_신호는_바꾼_탭이_없고_이상한_탭_ID_는_비운다() {
        assertThat(RealtimeHub.payload(null)).isEqualTo("{\"origin\":\"\"}");
        assertThat(RealtimeHub.payload("a\"}<script>")).isEqualTo("{\"origin\":\"\"}");
        assertThat(RealtimeHub.payload("3f2a-9c")).isEqualTo("{\"origin\":\"3f2a-9c\"}");
    }

    // --- 도움 ---

    private MvcResult 연결(long employeeId) throws Exception {
        로그인(employeeId);
        MvcResult result = mvc.perform(get("/api/realtime")).andReturn();
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
        return result;
    }

    private static void 로그인(long id) {
        Employee employee = Employee.builder().email(id + "@company.com").name("직원" + id).role(Role.EMPLOYEE).build();
        ReflectionTestUtils.setField(employee, "id", id);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static String 내용(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int 신호_수(MvcResult result) {
        return 내용(result).split("event:change", -1).length - 1;
    }

    /** 보내기는 전용 스레드에서 하므로 잠깐 기다린다(최대 3초). */
    private static void 기다림(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.get()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("3초 안에 신호가 오지 않았습니다");
            }
            Thread.sleep(20);
        }
        Thread.sleep(100); // 뒤따르는 신호가 있으면 마저 받는다
    }
}
