package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditLabels;
import com.company.leave.audit.AuditService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 발송을 이벤트 로그에 남긴다: 동작 "메일 발송", 대상 "메일", 상세에 종류·받는 사람·제목, 실패면 사유.
 */
@DisplayName("메일 발송 이벤트 로그")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailAuditTest {

    private final AuditService auditService = mock(AuditService.class);
    private final AuditMailRecorder recorder = new AuditMailRecorder(auditService);

    @Test
    void 성공하면_종류_받는_사람_참조_제목을_SYSTEM_으로_남긴다() {
        recorder.record("휴가 메일", List.of("a@company.com"), List.of("lead@company.com"), "[연차관리] 휴가 승인 안내", null);

        verify(auditService).record(null, "SYSTEM", "mail_send", "mail", null,
                "휴가 메일 · 받는 사람 a@company.com (참조 lead@company.com) · 제목: [연차관리] 휴가 승인 안내", true);
        assertThat(AuditLabels.action("mail_send")).isEqualTo("메일 발송");
        assertThat(AuditLabels.resource("mail")).isEqualTo("메일");
    }

    @Test
    void 실패하면_사유를_붙여_실패로_남긴다() {
        recorder.record("계정 생성", List.of("new@company.com"), List.of(), "[연차관리] 계정 안내",
                "Connection refused\n  nested exception is: java.net.ConnectException");

        verify(auditService).record(null, "SYSTEM", "mail_send", "mail", null,
                "계정 생성 · 받는 사람 new@company.com · 제목: [연차관리] 계정 안내 · 실패: Connection refused", false);
    }

    @Test
    void 받는_사람이_많으면_10명까지_적고_나머지는_인원만_적는다() {
        List<String> many = IntStream.rangeClosed(1, 13).mapToObj(i -> "u" + i + "@company.com").toList();

        String detail = AuditMailRecorder.detail("공지 메일", many, List.of(), "공지", null);

        assertThat(detail).contains("u10@company.com 외 3명").doesNotContain("u11@");
    }

    @Test
    void 기록이_실패해도_메일_흐름을_깨지_않는다() {
        doThrow(new IllegalStateException("db down")).when(auditService)
                .record(any(), anyString(), anyString(), anyString(), any(), anyString(), anyBoolean());

        recorder.record("휴가 메일", List.of("a@company.com"), List.of(), "제목", null); // 예외 없이 끝난다
    }

    @Test
    void 휴가_메일은_보내면_성공을_보내지_못하면_실패를_남긴다() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        List<String> results = new ArrayList<>();
        LeaveMailService service = new LeaveMailService(sender,
                new AccountMailProperties("no-reply@company.com", "http://localhost"),
                (kind, to, cc, subject, error) -> results.add(kind + "|" + to + "|" + (error == null ? "성공" : "실패")));
        LeaveMail mail = new LeaveMail(List.of("a@company.com"), "[연차관리] 휴가 결재 요청", "본문", "<p>본문</p>", null, null);

        service.onLeaveMail(mail);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));
        service.onLeaveMail(mail);

        assertThat(results).containsExactly("휴가 메일|[a@company.com]|성공", "휴가 메일|[a@company.com]|실패");
    }

    @Test
    void 공휴일_라이선스_대상과_동기화_동작도_한글로_보인다() {
        assertThat(AuditLabels.resource("holidays")).isEqualTo("공휴일");
        assertThat(AuditLabels.resource("license")).isEqualTo("라이선스");
        assertThat(AuditLabels.action("sync")).isEqualTo("동기화");
        assertThat(AuditLabels.action("blackout_reject")).isEqualTo("금지 기간 자동 반려");
        assertThat(AuditLabels.action("PASSWORD_RESET_REQUEST")).isEqualTo("비밀번호 찾기 요청");
    }
}
