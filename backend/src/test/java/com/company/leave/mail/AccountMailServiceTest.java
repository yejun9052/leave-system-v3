package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AccountMailService 단위 테스트. SMTP 대신 JavaMailSender 목으로 보낸 메시지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("계정 메일 발송")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccountMailServiceTest {

    private static final String FROM = "no-reply@company.com";
    private static final String BASE_URL = "https://leave.company.com";
    private static final String TO = "user@company.com";

    @Mock
    private JavaMailSender mailSender;

    @Test
    void 계정_생성_메일은_로그인_주소와_임시_비밀번호를_담아_보낸다() {
        서비스().onAccountCreated(new AccountMailEvents.AccountCreated(TO, "홍길동", "Tmp#Pass1234"));

        SimpleMailMessage message = 보낸_메일();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(TO);
        assertThat(message.getSubject()).contains("계정이 생성");
        assertThat(message.getText())
                .contains("홍길동님")
                .contains(BASE_URL + "/login")
                .contains("Tmp#Pass1234")
                .contains("첫 로그인 시 비밀번호를 변경해야 합니다");
    }

    @Test
    void 재설정_메일은_토큰을_담은_1회용_링크와_유효시간을_안내한다() {
        서비스().onPasswordReset(new AccountMailEvents.PasswordReset(TO, "홍길동", "abc-DEF_123"));

        SimpleMailMessage message = 보낸_메일();
        assertThat(message.getTo()).containsExactly(TO);
        assertThat(message.getSubject()).contains("비밀번호 재설정");
        assertThat(message.getText())
                .contains(BASE_URL + "/reset-password?token=abc-DEF_123")
                .contains("30분")
                .contains("한 번만")
                .contains("요청하지 않았다면 이 메일을 무시하세요");
    }

    @Test
    void 링크_기준_주소_끝에_슬래시가_있어도_주소가_중복되지_않는다() {
        new AccountMailService(mailSender, new AccountMailProperties(FROM, BASE_URL + "/"))
                .onPasswordReset(new AccountMailEvents.PasswordReset(TO, "홍길동", "token"));

        assertThat(보낸_메일().getText())
                .contains(BASE_URL + "/reset-password?token=token")
                .doesNotContain(BASE_URL + "//");
    }

    @Test
    void SMTP_발송에_실패해도_예외를_던지지_않는다() {
        doThrow(new MailSendException("SMTP 연결 실패")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> 서비스().onPasswordReset(
                new AccountMailEvents.PasswordReset(TO, "홍길동", "token")))
                .doesNotThrowAnyException();
    }

    @Test
    void 이벤트_로그에_임시_비밀번호와_토큰이_노출되지_않는다() {
        assertThat(new AccountMailEvents.AccountCreated(TO, "홍길동", "Tmp#Pass1234").toString())
                .contains(TO).doesNotContain("Tmp#Pass1234");
        assertThat(new AccountMailEvents.PasswordReset(TO, "홍길동", "secret-token").toString())
                .contains(TO).doesNotContain("secret-token");
    }

    @Test
    void 발송은_트랜잭션_커밋_후_비동기로_실행된다() throws NoSuchMethodException {
        for (String name : new String[] {"onAccountCreated", "onPasswordReset"}) {
            Method method = findMethod(name);
            assertThat(method.getAnnotation(Async.class)).as(name + " @Async").isNotNull();
            TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
            assertThat(listener).as(name + " @TransactionalEventListener").isNotNull();
            assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        }
    }

    // --- 테스트 도구 ---

    private AccountMailService 서비스() {
        return new AccountMailService(mailSender, new AccountMailProperties(FROM, BASE_URL));
    }

    private SimpleMailMessage 보낸_메일() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static Method findMethod(String name) throws NoSuchMethodException {
        for (Method m : AccountMailService.class.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new NoSuchMethodException(name);
    }
}
