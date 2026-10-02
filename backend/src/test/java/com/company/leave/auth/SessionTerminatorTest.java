package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 세션 종료")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionTerminatorTest {

    private static final long EMPLOYEE_ID = 9L;

    @Mock
    private FindByIndexNameSessionRepository<Session> sessionRepository;

    @Test
    void 모두_종료하면_그_사용자의_세션을_전부_삭제한다() {
        when(sessionRepository.findByPrincipalName("9")).thenReturn(세션들("a", "b"));

        new SessionTerminator(sessionRepository).terminateAll(EMPLOYEE_ID);

        verify(sessionRepository).deleteById("a");
        verify(sessionRepository).deleteById("b");
    }

    @Test
    void 비밀번호_변경_시_현재_세션은_남기고_다른_세션만_지운_뒤_현재_세션_ID를_바꾼다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession(null, "current");
        request.setSession(session);
        // 저장소에는 아직 옛(현재) ID 로 남아 있다
        when(sessionRepository.findByPrincipalName("9")).thenReturn(세션들("current", "other-device"));

        new SessionTerminator(sessionRepository).renewCurrentAndTerminateOthers(EMPLOYEE_ID, request);

        verify(sessionRepository).deleteById("other-device");
        verify(sessionRepository, never()).deleteById("current");
        assertThat(request.getSession().getId()).isNotEqualTo("current");
    }

    @Test
    void 다른_세션이_없으면_아무것도_지우지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "current"));
        when(sessionRepository.findByPrincipalName("9")).thenReturn(세션들("current"));

        new SessionTerminator(sessionRepository).renewCurrentAndTerminateOthers(EMPLOYEE_ID, request);

        verify(sessionRepository, never()).deleteById(anyString());
    }

    private Map<String, Session> 세션들(String... ids) {
        Map<String, Session> sessions = new LinkedHashMap<>();
        for (String id : ids) {
            sessions.put(id, null);
        }
        return sessions;
    }
}
