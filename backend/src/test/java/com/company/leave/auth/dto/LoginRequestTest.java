package com.company.leave.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("로그인 요청 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void 직원은_이메일로_로그인한다() {
        assertThat(validator.validate(new LoginRequest("user@company.com", "pw"))).isEmpty();
    }

    @Test
    void 관리_전용_계정은_이메일_형식이_아닌_아이디_admin으로_로그인한다() {
        assertThat(validator.validate(new LoginRequest("admin", "admin1234!"))).isEmpty();
    }

    @Test
    void 아이디나_비밀번호가_비어_있으면_거부한다() {
        assertThat(validator.validate(new LoginRequest(" ", "pw"))).isNotEmpty();
        assertThat(validator.validate(new LoginRequest("admin", ""))).isNotEmpty();
    }
}
