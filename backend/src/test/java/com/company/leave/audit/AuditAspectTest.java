package com.company.leave.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)의 자동 이벤트 로그: 경로에서 동작(HTTP 메서드나 끝의 행위 동사)·
 * 대상(/api 다음 칸)·id(마지막 숫자 칸)를 뽑아 성공/실패로 남긴다. 인증 경로는 남기지 않고(AuthService 가 따로 남김),
 * 기록 실패는 요청 처리를 막지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("이벤트 로그 자동 기록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditAspectTest {

    @Mock private AuditService auditService;
    @Mock private ProceedingJoinPoint pjp;

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(auditService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 성공한_요청은_HTTP_메서드를_동작으로_대상과_id와_함께_성공으로_남긴다() throws Throwable {
        요청("PUT", "/api/employees/20");
        when(pjp.proceed()).thenReturn("결과");

        Object result = aspect.around(pjp);

        assertThat(result).isEqualTo("결과");
        verify(auditService).record("PUT", "employees", "20", "/api/employees/20", true);
    }

    @Test
    void 경로_끝의_행위_동사를_동작으로_남긴다() throws Throwable {
        요청("POST", "/api/leave-requests/12/approve");

        aspect.around(pjp);

        verify(auditService).record("approve", "leave-requests", "12", "/api/leave-requests/12/approve", true);
    }

    @Test
    void 촉진_안내_발송은_생성이_아니라_발송으로_남긴다() throws Throwable {
        요청("POST", "/api/leave/promotion/send");

        aspect.around(pjp);

        verify(auditService).record("send", "leave", null, "/api/leave/promotion/send", true);
        assertThat(AuditLabels.action("send")).isEqualTo("촉진 안내 발송");
    }

    @Test
    void 취소_요청의_승인과_반려는_cancel_을_붙인_동작으로_남긴다() throws Throwable {
        요청("POST", "/api/leave-requests/12/cancel/approve");
        aspect.around(pjp);
        요청("POST", "/api/leave-requests/13/cancel/reject");
        aspect.around(pjp);

        verify(auditService).record("cancel_approve", "leave-requests", "12",
                "/api/leave-requests/12/cancel/approve", true);
        verify(auditService).record("cancel_reject", "leave-requests", "13",
                "/api/leave-requests/13/cancel/reject", true);
    }

    @Test
    void 숫자가_없는_경로는_id_없이_남기고_모르는_끝_단어는_HTTP_메서드로_본다() throws Throwable {
        요청("POST", "/api/policy/blackouts");

        aspect.around(pjp);

        verify(auditService).record(eq("POST"), eq("policy"), isNull(), eq("/api/policy/blackouts"), eq(true));
    }

    @Test
    void 실패한_요청은_예외_종류와_메시지를_붙여_실패로_남기고_예외를_그대로_던진다() throws Throwable {
        요청("DELETE", "/api/departments/5");
        IllegalStateException failure = new IllegalStateException("하위 부서가 있습니다");
        when(pjp.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(pjp)).isSameAs(failure);

        verify(auditService).record("DELETE", "departments", "5",
                "/api/departments/5 | IllegalStateException: 하위 부서가 있습니다", false);
    }

    @Test
    void 실패_메시지는_300자까지만_남긴다() throws Throwable {
        요청("PATCH", "/api/employees/1");
        when(pjp.proceed()).thenThrow(new RuntimeException("가".repeat(400)));

        assertThatThrownBy(() -> aspect.around(pjp)).isInstanceOf(RuntimeException.class);

        verify(auditService).record("PATCH", "employees", "1",
                "/api/employees/1 | RuntimeException: " + "가".repeat(300), false);
    }

    @Test
    void 인증_경로는_성공해도_실패해도_남기지_않는다() throws Throwable {
        요청("POST", "/api/auth/login");
        aspect.around(pjp);
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("비밀번호 오류"));

        assertThatThrownBy(() -> aspect.around(pjp)).isInstanceOf(IllegalArgumentException.class);

        verify(auditService, never()).record(anyString(), any(), any(), any(), anyBoolean());
    }

    @Test
    void 기록에_실패해도_요청_결과는_그대로_돌려준다() throws Throwable {
        요청("POST", "/api/leave-requests");
        when(pjp.proceed()).thenReturn("저장됨");
        doThrow(new RuntimeException("DB 끊김")).when(auditService)
                .record(anyString(), any(), any(), any(), anyBoolean());

        assertThat(aspect.around(pjp)).isEqualTo("저장됨");
    }

    @Test
    void 요청_밖에서_불리면_메서드_이름을_경로로_보고_CALL_로_남긴다() throws Throwable {
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("LeaveAdminController.grantAll(..)");
        when(pjp.getSignature()).thenReturn(signature);

        aspect.around(pjp);

        verify(auditService).record("CALL", "LeaveAdminController.grantAll(..)", null,
                "LeaveAdminController.grantAll(..)", true);
    }

    // --- helpers ---

    @Test
    void 리포트_내보내기는_조회_조건과_함께_export로_남긴다() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reports/leave-usage/export");
        request.setQueryString("year=2026&departmentIds=26&departmentIds=27&employeeIds=112");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.aroundGet(pjp);

        verify(auditService).record("export", "reports", null,
                "/api/reports/leave-usage/export?year=2026&departmentIds=26&departmentIds=27&employeeIds=112", true);
    }

    @Test
    void 사용자_엑셀_내보내기도_남긴다() throws Throwable {
        요청("GET", "/api/employees/export");

        aspect.aroundGet(pjp);

        verify(auditService).record("export", "employees", null, "/api/employees/export", true);
    }

    @Test
    void 내보내기가_실패해도_실패로_남기고_예외는_그대로_던진다() throws Throwable {
        요청("GET", "/api/employees/export");
        when(pjp.proceed()).thenThrow(new IllegalStateException("엑셀 오류"));

        assertThatThrownBy(() -> aspect.aroundGet(pjp)).isInstanceOf(IllegalStateException.class);

        verify(auditService).record(eq("export"), eq("employees"), isNull(),
                eq("/api/employees/export | IllegalStateException: 엑셀 오류"), eq(false));
    }

    @Test
    void 내보내기가_아닌_조회는_남기지_않는다() throws Throwable {
        요청("GET", "/api/employees");
        when(pjp.proceed()).thenReturn("목록");

        assertThat(aspect.aroundGet(pjp)).isEqualTo("목록");

        verify(auditService, never()).record(anyString(), anyString(), any(), anyString(), anyBoolean());
    }

    private static void 요청(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
