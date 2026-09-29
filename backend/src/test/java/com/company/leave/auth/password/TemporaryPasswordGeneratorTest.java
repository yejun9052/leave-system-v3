package com.company.leave.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("임시 비밀번호 생성")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TemporaryPasswordGeneratorTest {

    private static final int 반복_횟수 = 2_000;

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void 항상_12자다() {
        for (int i = 0; i < 반복_횟수; i++) {
            assertThat(generator.generate()).hasSize(12);
        }
    }

    @Test
    void 대문자_소문자_숫자_특수문자를_각각_하나_이상_포함한다() {
        for (int i = 0; i < 반복_횟수; i++) {
            String password = generator.generate();
            assertThat(password).as(password)
                    .containsPattern("[A-Z]")
                    .containsPattern("[a-z]")
                    .containsPattern("[0-9]")
                    .matches(p -> p.chars().anyMatch(c -> TemporaryPasswordGenerator.SPECIAL.indexOf(c) >= 0));
        }
    }

    @Test
    void 헷갈리기_쉬운_문자는_쓰지_않는다() {
        for (int i = 0; i < 반복_횟수; i++) {
            assertThat(generator.generate()).doesNotContainPattern("[0O1lI]");
        }
    }

    @Test
    void 호출할_때마다_다른_값이_나온다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 반복_횟수; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(반복_횟수);
    }
}
