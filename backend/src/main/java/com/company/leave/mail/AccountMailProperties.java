package com.company.leave.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 계정 메일 설정 (app.mail.*).
 *
 * @param from        보내는 사람 주소
 * @param linkBaseUrl 메일 속 링크의 기준 주소(APP_ORIGIN, 예: https://leave.회사.com)
 */
@ConfigurationProperties(prefix = "app.mail")
public record AccountMailProperties(String from, String linkBaseUrl) {
}
