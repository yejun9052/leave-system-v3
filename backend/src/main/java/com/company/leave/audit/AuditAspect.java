package com.company.leave.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 모든 상태 변경 요청(POST/PUT/PATCH/DELETE)을 자동으로 감사 로그에 기록한다.
 * 인증 관련(/api/auth/*)은 AuthService 에서 별도로 남긴다.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    void postMapping() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    void putMapping() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    void patchMapping() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    void deleteMapping() {
    }

    @Around("postMapping() || putMapping() || patchMapping() || deleteMapping()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        String uri = request != null ? request.getRequestURI() : pjp.getSignature().toShortString();
        String httpMethod = request != null ? request.getMethod() : "CALL";
        boolean auth = uri.startsWith("/api/auth/");
        String action = actionOf(uri, httpMethod);

        try {
            Object result = pjp.proceed();
            if (!auth) {
                safeRecord(action, resourceOf(uri), idOf(uri), uri, true);
            }
            return result;
        } catch (Throwable ex) {
            if (!auth) {
                safeRecord(action, resourceOf(uri), idOf(uri),
                        uri + " | " + ex.getClass().getSimpleName()
                                + ": " + safeMessage(ex), false);
            }
            throw ex;
        }
    }

    private static final java.util.Set<String> ACTION_VERBS = java.util.Set.of(
            "approve", "reject", "cancel", "move", "reactivate", "password",
            "grant", "run", "import", "export", "read-all");

    /**
     * 경로 끝의 행위 동사를 인식해 의미 있는 동작명을 만든다.
     * 예) .../4/cancel → cancel, .../4/cancel/reject → cancel_reject, 없으면 HTTP 메서드.
     */
    private String actionOf(String uri, String httpMethod) {
        String[] parts = uri.split("/");
        if (parts.length == 0) {
            return httpMethod;
        }
        String last = parts[parts.length - 1];
        if (last.isEmpty() || last.matches("\\d+")) {
            return httpMethod;
        }
        if (ACTION_VERBS.contains(last)) {
            String prev = parts.length >= 2 ? parts[parts.length - 2] : "";
            if ("cancel".equals(prev)) {
                return "cancel_" + last; // cancel_approve / cancel_reject
            }
            return last;
        }
        return httpMethod;
    }

    private void safeRecord(String action, String entityType, String entityId,
                            String detail, boolean success) {
        try {
            auditService.record(action, entityType, entityId, detail, success);
        } catch (RuntimeException e) {
            log.warn("감사 로그 기록 실패: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /** /api/{resource}/... 에서 resource 세그먼트 추출. */
    private String resourceOf(String uri) {
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("api")) {
                return parts[i + 1];
            }
        }
        return uri;
    }

    /** 경로에 포함된 숫자 id 추출(마지막 숫자 세그먼트). */
    private String idOf(String uri) {
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        return null;
    }

    private String safeMessage(Throwable ex) {
        String m = ex.getMessage();
        if (m == null) {
            return "";
        }
        return m.length() > 300 ? m.substring(0, 300) : m;
    }
}
