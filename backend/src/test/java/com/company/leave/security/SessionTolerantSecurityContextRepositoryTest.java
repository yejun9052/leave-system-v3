package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 역직렬화할 수 없는 세션(배포 후 클래스 구조 변경 등)을 만나면 500 대신 세션을 폐기하고 미인증으로 처리하는지.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("역직렬화 실패 세션 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionTolerantSecurityContextRepositoryTest {

    @Mock
    private SecurityContextRepository delegate;

    private SessionTolerantSecurityContextRepository repository;
    private MockHttpServletRequest request;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        repository = new SessionTolerantSecurityContextRepository(delegate);
        request = new MockHttpServletRequest("GET", "/api/auth/me");
        session = new MockHttpSession();
        request.setSession(session);
    }

    @Test
    void 정상_세션은_저장된_인증_정보를_그대로_돌려준다() {
        SecurityContext stored = new SecurityContextImpl(new TestingAuthenticationToken("user", null, "ROLE_EMPLOYEE"));
        when(delegate.loadDeferredContext(request)).thenReturn(지연_컨텍스트(() -> stored, false));

        DeferredSecurityContext loaded = repository.loadDeferredContext(request);

        assertThat(loaded.get()).isSameAs(stored);
        assertThat(loaded.isGenerated()).isFalse();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void 역직렬화에_실패하면_세션을_폐기하고_빈_인증_정보를_돌려준다() {
        when(delegate.loadDeferredContext(request)).thenReturn(지연_컨텍스트(() -> {
            throw new IllegalStateException("세션 속성 읽기 실패",
                    new SerializationFailedException("클래스 구조가 바뀜"));
        }, false));

        DeferredSecurityContext loaded = repository.loadDeferredContext(request);

        assertThat(loaded.get().getAuthentication()).isNull();
        assertThat(loaded.isGenerated()).isTrue();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 역직렬화_실패가_아닌_예외는_숨기지_않고_그대로_던진다() {
        when(delegate.loadDeferredContext(request)).thenReturn(지연_컨텍스트(() -> {
            throw new IllegalStateException("DB 연결 끊김");
        }, false));

        DeferredSecurityContext loaded = repository.loadDeferredContext(request);

        assertThatThrownBy(loaded::get).isInstanceOf(IllegalStateException.class).hasMessage("DB 연결 끊김");
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void 인증_정보는_처음_한_번만_읽는다() {
        int[] calls = {0};
        SecurityContext stored = new SecurityContextImpl();
        when(delegate.loadDeferredContext(request)).thenReturn(지연_컨텍스트(() -> {
            calls[0]++;
            return stored;
        }, false));

        DeferredSecurityContext loaded = repository.loadDeferredContext(request);
        loaded.get();
        loaded.isGenerated();
        loaded.get();

        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void 세션_존재_확인_중_역직렬화에_실패하면_없음으로_보고_세션을_폐기한다() {
        when(delegate.containsContext(request)).thenThrow(new SerializationFailedException("복원 불가"));

        assertThat(repository.containsContext(request)).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 저장은_원래_저장소에_그대로_맡긴다() {
        SecurityContext context = new SecurityContextImpl();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveContext(context, request, response);

        verify(delegate).saveContext(context, request, response);
    }

    private static DeferredSecurityContext 지연_컨텍스트(Supplier<SecurityContext> supplier, boolean generated) {
        return new DeferredSecurityContext() {
            @Override
            public SecurityContext get() {
                return supplier.get();
            }

            @Override
            public boolean isGenerated() {
                return generated;
            }
        };
    }
}
