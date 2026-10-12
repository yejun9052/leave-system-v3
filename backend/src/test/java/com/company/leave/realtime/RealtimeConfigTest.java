package com.company.leave.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * 실시간 신호를 보낼 요청 범위(RealtimeConfig): 데이터를 바꾸는 /api 요청만.
 * 로그인·로그아웃·비밀번호 찾기(/api/auth/**)와 실시간 연결 자체는 다른 사람 화면과 무관해 뺀다.
 */
@DisplayName("실시간 신호 범위")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RealtimeConfigTest {

    /** InterceptorRegistry 가 만든 등록 결과를 꺼내 본다 */
    private static final class Registry extends InterceptorRegistry {
        List<Object> registered() {
            return getInterceptors();
        }
    }

    private static MappedInterceptor mapped() {
        Registry registry = new Registry();
        new RealtimeConfig(mock(RealtimeHub.class)).addInterceptors(registry);
        assertThat(registry.registered()).singleElement().isInstanceOf(MappedInterceptor.class);
        return (MappedInterceptor) registry.registered().get(0);
    }

    @ParameterizedTest(name = "{0} {1} → 신호 대상 {2}")
    @CsvSource({
            "POST, /api/auth/login, false",
            "POST, /api/auth/logout, false",
            "POST, /api/auth/password-reset/request, false",
            "POST, /api/auth/password-reset/confirm, false",
            "GET, /api/realtime, false",
            "POST, /api/leave-requests/1/approve, true",
            "PUT, /api/backups/settings, true",
            "POST, /api/notifications/read-all, true",
    })
    void 로그인_로그아웃_비밀번호_찾기와_실시간_연결은_신호_대상이_아니다(String method, String uri, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        ServletRequestPathUtils.parseAndCache(request); // 실제 요청에서는 DispatcherServlet 이 해 준다

        assertThat(mapped().matches(request)).isEqualTo(expected);
    }
}
