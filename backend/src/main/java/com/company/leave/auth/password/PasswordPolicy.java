package com.company.leave.auth.password;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 직원이 직접 정하는 새 비밀번호의 규칙(내 비밀번호 변경·재설정 링크). 이미 쓰고 있는 비밀번호는 다시 검사하지 않는다.
 * <ul>
 *   <li>8자 이상, UTF-8 72바이트 이하(BCrypt 가 72바이트까지만 받는다. 한글은 한 글자에 3바이트라 24자)</li>
 *   <li>영문·숫자·그 밖의 문자(특수문자·한글 등) 중 2종류 이상. 같은 문자만 반복한 것(11111111)도 여기서 걸린다</li>
 *   <li>아이디(이메일 앞부분, 3자 이상일 때) 포함 금지(대소문자 무시)</li>
 * </ul>
 * 화면(passwordSchema.ts)도 같은 규칙으로 먼저 막는다.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_BYTES = 72;
    /** 아이디가 이보다 짧으면 포함 검사를 하지 않는다(한두 글자 아이디가 흔한 글자를 막지 않게) */
    static final int MIN_ID_LENGTH_TO_CHECK = 3;

    private PasswordPolicy() {
    }

    /** 규칙에 맞지 않으면 이유를 담은 BusinessException(INVALID_INPUT). loginId: 그 직원의 이메일(관리 계정은 아이디) */
    public static void check(String password, String loginId) {
        String problem = problem(password, loginId);
        if (problem != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, problem);
        }
    }

    /** 규칙에 맞지 않는 이유(사용자에게 보여 줄 문장), 맞으면 null */
    static String problem(String password, String loginId) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return "비밀번호는 " + MIN_LENGTH + "자 이상이어야 합니다.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "비밀번호가 너무 깁니다. 영문·숫자·특수문자는 72자, 한글은 24자까지 쓸 수 있습니다.";
        }
        boolean letter = false;
        boolean digit = false;
        boolean other = false;
        for (int i = 0; i < password.length(); ) {
            int c = password.codePointAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                letter = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                other = true;
            }
            i += Character.charCount(c);
        }
        if ((letter ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0) < 2) {
            return "영문, 숫자, 특수문자 중 2종류 이상을 섞어 주세요.";
        }
        String id = localPart(loginId);
        if (id.length() >= MIN_ID_LENGTH_TO_CHECK && password.toLowerCase(Locale.ROOT).contains(id)) {
            return "비밀번호에 아이디(이메일 @ 앞부분)를 넣을 수 없습니다.";
        }
        return null;
    }

    private static String localPart(String loginId) {
        if (loginId == null) {
            return "";
        }
        int at = loginId.indexOf('@');
        return (at >= 0 ? loginId.substring(0, at) : loginId).trim().toLowerCase(Locale.ROOT);
    }
}
