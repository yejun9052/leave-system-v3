package com.company.leave.mail;

import com.company.leave.audit.AuditService;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 메일 발송을 이벤트 로그에 남긴다: 동작 "메일 발송", 대상 "메일", 상세에 종류·받는 사람·제목, 실패면 사유.
 * 메일은 커밋 뒤 비동기로 보내 로그인 사용자가 없으므로 SYSTEM 으로 기록한다.
 */
@Component
public class AuditMailRecorder implements MailRecorder {

    static final String ACTION = "mail_send";
    static final String ENTITY = "mail";
    /** 받는 사람이 많으면(공지 메일) 앞쪽만 적고 "외 N명" */
    static final int MAX_LISTED = 10;
    /** audit_logs.detail 길이 */
    private static final int MAX_DETAIL = 1000;

    private static final Logger log = LoggerFactory.getLogger(AuditMailRecorder.class);

    private final AuditService auditService;

    public AuditMailRecorder(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String kind, Collection<String> to, Collection<String> cc, String subject, String error) {
        try {
            auditService.record(null, "SYSTEM", ACTION, ENTITY, null, detail(kind, to, cc, subject, error), error == null);
        } catch (RuntimeException ex) {
            // 기록 실패가 메일 발송 흐름을 깨지 않게 한다
            log.warn("메일 발송 기록 실패: {}", ex.getMessage());
        }
    }

    static String detail(String kind, Collection<String> to, Collection<String> cc, String subject, String error) {
        StringBuilder sb = new StringBuilder(kind).append(" · 받는 사람 ").append(list(to));
        if (cc != null && !cc.isEmpty()) {
            sb.append(" (참조 ").append(list(cc)).append(')');
        }
        sb.append(" · 제목: ").append(subject);
        if (error != null) {
            sb.append(" · 실패: ").append(error.lines().findFirst().orElse(error).strip()); // 첫 줄만
        }
        return sb.length() > MAX_DETAIL ? sb.substring(0, MAX_DETAIL - 1) + "…" : sb.toString();
    }

    private static String list(Collection<String> addresses) {
        List<String> all = List.copyOf(addresses);
        if (all.size() <= MAX_LISTED) {
            return String.join(", ", all);
        }
        return String.join(", ", all.subList(0, MAX_LISTED)) + " 외 " + (all.size() - MAX_LISTED) + "명";
    }
}
