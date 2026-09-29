package com.company.leave.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 세션(DB)에 저장된 인증 정보를 역직렬화하지 못하는 경우 — 배포로 클래스 구조나 Spring Security
 * 버전이 바뀐 뒤 기존 세션을 읽을 때 등 — 모든 요청이 500 이 되지 않도록, 해당 세션을 폐기하고
 * 미인증으로 처리한다(사용자는 재로그인). 그 외 예외는 그대로 던진다.
 */
public class SessionTolerantSecurityContextRepository implements SecurityContextRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionTolerantSecurityContextRepository.class);

    private final SecurityContextRepository delegate;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public SessionTolerantSecurityContextRepository(SecurityContextRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        DeferredSecurityContext deferred = delegate.loadDeferredContext(request);
        return new DeferredSecurityContext() {

            private SecurityContext context;
            private boolean generated;

            @Override
            public SecurityContext get() {
                if (context == null) {
                    try {
                        context = deferred.get();
                        generated = deferred.isGenerated();
                    } catch (RuntimeException ex) {
                        discardUnreadableSession(request, ex);
                        context = contextHolderStrategy.createEmptyContext();
                        generated = true;
                    }
                }
                return context;
            }

            @Override
            public boolean isGenerated() {
                get();
                return generated;
            }
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        try {
            return delegate.loadContext(requestResponseHolder);
        } catch (RuntimeException ex) {
            discardUnreadableSession(requestResponseHolder.getRequest(), ex);
            return contextHolderStrategy.createEmptyContext();
        }
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveContext(context, request, response);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        try {
            return delegate.containsContext(request);
        } catch (RuntimeException ex) {
            discardUnreadableSession(request, ex);
            return false;
        }
    }

    /** 역직렬화 실패면 세션 폐기, 아니면 원래 예외를 다시 던진다. */
    private void discardUnreadableSession(HttpServletRequest request, RuntimeException ex) {
        if (!isDeserializationFailure(ex)) {
            throw ex;
        }
        log.warn("세션의 인증 정보를 복원할 수 없어 세션을 폐기합니다(재로그인 필요): {}", ex.getMessage());
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // 이미 폐기됨
            }
        }
    }

    private static boolean isDeserializationFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SerializationFailedException) {
                return true;
            }
        }
        return false;
    }
}
