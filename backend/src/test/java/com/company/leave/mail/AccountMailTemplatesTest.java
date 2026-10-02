package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 계정 메일(계정 생성·비밀번호 재설정·연차 소멸) 제목과 본문, 링크 주소.
 */
@DisplayName("계정 메일 문구")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccountMailTemplatesTest {

    @Test
    void 계정_생성_메일에는_로그인_주소와_임시_비밀번호와_변경_안내가_들어간다() {
        AccountMailTemplates.Mail mail = AccountMailTemplates.accountCreated("http://localhost:5173", "홍길동", "Tmp-1234");

        assertThat(mail.subject()).isEqualTo("[연차관리] 계정이 생성되었습니다");
        assertThat(mail.body())
                .startsWith("홍길동님, 연차관리 시스템 계정이 생성되었습니다.")
                .contains("로그인 주소: http://localhost:5173/login")
                .contains("임시 비밀번호: Tmp-1234")
                .contains("첫 로그인 시 비밀번호를 변경해야 합니다.");
    }

    @Test
    void 기본_주소_끝의_슬래시는_한_번만_쓴다() {
        assertThat(AccountMailTemplates.accountCreated("http://localhost:5173/", "홍길동", "x").body())
                .contains("로그인 주소: http://localhost:5173/login")
                .doesNotContain("5173//login");
    }

    @Test
    void 비밀번호_재설정_메일에는_토큰_링크와_유효_시간을_넣는다() {
        AccountMailTemplates.Mail mail = AccountMailTemplates.passwordReset("http://localhost:5173", "홍길동", "abc123");

        assertThat(mail.subject()).isEqualTo("[연차관리] 비밀번호 재설정 안내");
        assertThat(mail.body())
                .contains("http://localhost:5173/reset-password?token=abc123")
                .contains("링크는 30분 동안 유효하며 한 번만 사용할 수 있습니다.")
                .contains("기존 비밀번호는 바뀌지 않습니다.");
    }

    @Test
    void Base64URL_토큰의_하이픈과_밑줄은_그대로_링크에_들어간다() {
        String body = AccountMailTemplates.passwordReset("http://localhost:5173", "홍길동", "Ab-_9xZ").body();

        assertThat(body).contains("/reset-password?token=Ab-_9xZ\n");
    }

    @Test
    void 연차_소멸_메일에는_휴가_종류와_소멸_일수와_취소_시_복구_안내가_들어간다() {
        AccountMailTemplates.Mail mail = AccountMailTemplates.leaveForfeited(
                "http://localhost:5173", "홍길동", "병가", "0.5", "2026-10-05 ~ 2026-10-06");

        assertThat(mail.subject()).isEqualTo("[연차관리] 잔여 연차 0.5일 소멸 안내");
        assertThat(mail.body())
                .startsWith("홍길동님, 병가(2026-10-05 ~ 2026-10-06)가 승인되었습니다.")
                .contains("남아 있던 연차 0.5일이 소멸되었습니다.")
                .contains("내 휴가: http://localhost:5173/my-leaves")
                .contains("해당 병가가 취소되면 소멸된 연차는 다시 돌아옵니다.");
    }
}
