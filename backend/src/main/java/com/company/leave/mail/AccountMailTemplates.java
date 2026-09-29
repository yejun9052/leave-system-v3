package com.company.leave.mail;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * 계정 메일 본문(일반 텍스트). 템플릿 엔진 없이 문자열로 만든다.
 */
public final class AccountMailTemplates {

    /** 재설정 링크 유효 시간(분). 3단계 PasswordResetService 의 만료 시간과 같아야 한다. */
    public static final int RESET_LINK_VALID_MINUTES = 30;

    private AccountMailTemplates() {
    }

    public record Mail(String subject, String body) {
    }

    public static Mail accountCreated(String baseUrl, String name, String temporaryPassword) {
        String body = name + "님, 연차관리 시스템 계정이 생성되었습니다.\n"
                + "\n"
                + "로그인 주소: " + url(baseUrl, "/login") + "\n"
                + "임시 비밀번호: " + temporaryPassword + "\n"
                + "\n"
                + "첫 로그인 시 비밀번호를 변경해야 합니다.\n"
                + "본인이 요청하지 않은 계정이라면 인사 담당자에게 알려 주세요.\n";
        return new Mail("[연차관리] 계정이 생성되었습니다", body);
    }

    public static Mail passwordReset(String baseUrl, String name, String resetToken) {
        String link = UriComponentsBuilder.fromUriString(url(baseUrl, "/reset-password"))
                .queryParam("token", resetToken)
                .encode()
                .toUriString();
        String body = name + "님, 비밀번호 재설정 요청을 받았습니다.\n"
                + "\n"
                + "아래 링크에서 새 비밀번호를 설정하세요.\n"
                + link + "\n"
                + "\n"
                + "링크는 " + RESET_LINK_VALID_MINUTES + "분 동안 유효하며 한 번만 사용할 수 있습니다.\n"
                + "요청하지 않았다면 이 메일을 무시하세요. 기존 비밀번호는 바뀌지 않습니다.\n";
        return new Mail("[연차관리] 비밀번호 재설정 안내", body);
    }

    private static String url(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
