package com.company.leave.calendar.holiday;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 공휴일 API 설정 (app.holiday-api.*).
 *
 * @param baseUrl    특일 정보 서비스 기준 주소
 * @param serviceKey 공공데이터포털 서비스 키(Decoding 키). 비어 있으면 동기화하지 않는다
 */
@ConfigurationProperties(prefix = "app.holiday-api")
public record HolidayApiProperties(String baseUrl, String serviceKey) {

    public boolean configured() {
        return StringUtils.hasText(serviceKey);
    }
}
