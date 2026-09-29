package com.company.leave.license;

import java.time.LocalDate;

/**
 * 라이선스에 담기는 정보(서명 대상).
 *
 * @param id        라이선스 식별자
 * @param licensee  사용 회사명(표기용)
 * @param issuedAt  발급일
 * @param expiresAt 만료일 (이 날짜까지 유효)
 * @param maxUsers  최대 재직 사용자 수 (0 = 무제한)
 * @param host      설치처 고정 값(도메인 등). 비어있으면 설치처 검사 안 함
 */
public record LicensePayload(
        String id,
        String licensee,
        LocalDate issuedAt,
        LocalDate expiresAt,
        int maxUsers,
        String host) {
}
