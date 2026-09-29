package com.company.leave.auth.password;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 임시 비밀번호 생성: 12자, 대문자·소문자·숫자·특수문자 각 1개 이상, SecureRandom.
 * 메일로 받아 직접 입력하므로 헷갈리기 쉬운 문자(0/O, 1/l/I)는 뺀다.
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 12;
    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SPECIAL = "!@#$%^&*?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[LENGTH];
        // 네 종류를 한 글자씩 먼저 채운 뒤 나머지는 전체 문자에서 뽑고, 자리를 섞는다
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SPECIAL);
        for (int i = 4; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
