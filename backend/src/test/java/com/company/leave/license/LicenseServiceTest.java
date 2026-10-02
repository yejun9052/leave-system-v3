package com.company.leave.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 라이선스 검증과 사용자 정원 검사.
 * 앱에는 벤더 공개 키만 내장되어 있어 테스트에서 "유효한" 라이선스를 발급할 수 없다. 그래서 검증 실패 경로(키 없음·형식 오류·
 * 서명 불일치·파싱 오류)는 실제 문자열로, 정원 검사는 유효 상태를 직접 넣어 확인한다.
 */
@DisplayName("라이선스 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LicenseServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 키가_없으면_유효하지_않다() {
        LicenseService service = 서비스("");

        assertThat(service.valid()).isFalse();
        assertThat(service.status().reason()).isEqualTo("라이선스 키가 설정되지 않았습니다.");
        assertThat(service.status().enforced()).isTrue();
    }

    @Test
    void 점으로_나뉜_두_부분이_아니면_형식_오류다() {
        assertThat(서비스("abc").status().reason()).isEqualTo("라이선스 형식 오류");
        assertThat(서비스("a.b.c").status().reason()).isEqualTo("라이선스 형식 오류");
    }

    @Test
    void 내장_공개_키와_짝이_아닌_키로_서명하면_위조로_본다() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = LicenseCrypto.utf8(
                "{\"id\":\"L-1\",\"licensee\":\"테스트 회사\",\"expiresAt\":\"2099-12-31\",\"maxUsers\":0}");
        String token = LicenseCrypto.b64url(payload) + "."
                + LicenseCrypto.b64url(LicenseCrypto.sign(attacker.getPrivate(), payload));

        LicenseService service = 서비스(token);

        assertThat(service.valid()).isFalse();
        assertThat(service.status().reason()).isEqualTo("서명 검증 실패(위조 또는 손상)");
    }

    @Test
    void Base64가_깨진_키는_파싱_오류로_본다() {
        LicenseService service = 서비스("@@@.###");

        assertThat(service.valid()).isFalse();
        assertThat(service.status().reason()).startsWith("라이선스 파싱 오류");
    }

    @Test
    void 초기화_전에도_상태를_물으면_그때_검사한다() {
        LicenseService service = new LicenseService(objectMapper, "", "");

        assertThat(service.valid()).isFalse();
        assertThat(service.status().reason()).isEqualTo("라이선스 키가 설정되지 않았습니다.");
    }

    @Test
    void 라이선스가_유효하지_않으면_정원_검사는_하지_않는다() {
        LicenseService service = 서비스("");

        assertThatCode(() -> service.checkUserQuota(10_000)).doesNotThrowAnyException();
    }

    @Test
    void 정원이_있으면_재직자가_정원에_닿는_순간부터_막는다() {
        LicenseService service = 유효한_서비스(10);

        assertThatCode(() -> service.checkUserQuota(9)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkUserQuota(10))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LICENSE_USER_LIMIT);
                    assertThat(ex.getMessage()).contains("10명");
                });
    }

    @Test
    void 정원이_0이면_제한이_없다() {
        LicenseService service = 유효한_서비스(0);

        assertThatCode(() -> service.checkUserQuota(100_000)).doesNotThrowAnyException();
        assertThat(service.valid()).isTrue();
    }

    @Test
    void 유효_상태에는_사용처와_만료일과_남은_날이_담긴다() {
        LicensePayload p = new LicensePayload("L-1", "테스트 회사", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), 50, null);

        LicenseService.LicenseStatus s = LicenseService.LicenseStatus.valid(p, 91);

        assertThat(s.valid()).isTrue();
        assertThat(s.reason()).isEqualTo("OK");
        assertThat(s.licensee()).isEqualTo("테스트 회사");
        assertThat(s.expiresAt()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(s.daysLeft()).isEqualTo(91);
        assertThat(s.maxUsers()).isEqualTo(50);
        assertThat(LicenseService.LicenseStatus.expiredOf(p).reason()).isEqualTo("라이선스가 만료되었습니다.");
        assertThat(LicenseService.LicenseStatus.expiredOf(p).valid()).isFalse();
    }

    private LicenseService 서비스(String key) {
        LicenseService service = new LicenseService(objectMapper, key, "");
        service.init();
        return service;
    }

    /** 벤더 비공개 키 없이는 유효 라이선스를 만들 수 없어 유효 상태를 직접 넣는다. */
    private LicenseService 유효한_서비스(int maxUsers) {
        LicenseService service = 서비스("");
        LicensePayload p = new LicensePayload("L-1", "테스트 회사", LocalDate.now(), LocalDate.now().plusYears(1),
                maxUsers, null);
        ReflectionTestUtils.setField(service, "status", LicenseService.LicenseStatus.valid(p, 365));
        return service;
    }
}
