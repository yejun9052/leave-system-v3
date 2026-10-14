package com.company.leave.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("새 비밀번호 규칙")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordPolicyTest {

    private static final String EMAIL = "hong.gildong@company.com";

    @Test
    void 영문과_숫자를_섞은_8자_이상은_통과한다() {
        assertThat(PasswordPolicy.problem("spring2026", EMAIL)).isNull();
        assertThat(PasswordPolicy.problem("leave!plan", EMAIL)).isNull();
        assertThat(PasswordPolicy.problem("연차관리2026", EMAIL)).isNull();
    }

    @Test
    void 여덟_자보다_짧으면_막는다() {
        assertThat(PasswordPolicy.problem("ab12!@#", EMAIL)).isEqualTo("비밀번호는 8자 이상이어야 합니다.");
    }

    @Test
    void 한_종류의_문자만_쓰면_막는다() {
        assertThat(PasswordPolicy.problem("abcdefgh", EMAIL)).contains("2종류 이상");
        assertThat(PasswordPolicy.problem("12345678", EMAIL)).contains("2종류 이상");
        assertThat(PasswordPolicy.problem("!@#$%^&*", EMAIL)).contains("2종류 이상");
        assertThat(PasswordPolicy.problem("가나다라마바사아", EMAIL)).contains("2종류 이상");
    }

    @Test
    void 같은_문자만_반복하면_막는다() {
        assertThat(PasswordPolicy.problem("11111111", EMAIL)).isNotNull();
        assertThat(PasswordPolicy.problem("aaaaaaaaaa", EMAIL)).isNotNull();
    }

    @Test
    void 이메일_앞부분이_들어가면_대소문자와_관계없이_막는다() {
        assertThat(PasswordPolicy.problem("Hong.Gildong1", EMAIL)).contains("아이디");
        assertThat(PasswordPolicy.problem("2026hong.gildong!", EMAIL)).contains("아이디");
    }

    @Test
    void 관리_계정은_아이디_자체를_넣을_수_없다() {
        assertThat(PasswordPolicy.problem("admin1234!", "admin")).contains("아이디");
    }

    @Test
    void 두_글자_아이디는_포함_검사를_하지_않는다() {
        assertThat(PasswordPolicy.problem("kimchi2026", "ki@company.com")).isNull();
    }

    @Test
    void 영문_숫자는_72자까지_통과하고_73자부터_막는다() {
        String seventyTwo = "a1".repeat(36);

        assertThat(PasswordPolicy.problem(seventyTwo, EMAIL)).isNull();
        assertThat(PasswordPolicy.problem(seventyTwo + "b", EMAIL)).contains("너무 깁니다");
    }

    @Test
    void 한글은_24자까지_통과하고_그보다_길면_서버_오류_대신_안내한다() {
        // 한글은 한 글자에 3바이트: 23자 + 숫자 3개 = 72바이트, 25자 = 75바이트(BCrypt 한도 72바이트를 넘음)
        assertThat(PasswordPolicy.problem("가".repeat(23) + "123", EMAIL)).isNull();
        assertThat(PasswordPolicy.problem("가".repeat(24) + "1", EMAIL)).contains("너무 깁니다");
    }

    @Test
    void 규칙에_맞지_않으면_입력_오류로_이유를_알려준다() {
        assertThatThrownBy(() -> PasswordPolicy.check("abcdefgh", EMAIL))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(ex.getMessage()).contains("2종류 이상");
                });
    }

    @Test
    void 비밀번호가_없으면_짧다고_알린다() {
        assertThat(PasswordPolicy.problem(null, EMAIL)).contains("8자 이상");
    }
}
