package com.company.leave.license;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 라이선스 검증/집행.
 *
 * <p>앱에는 <b>공개 키만</b> 내장되어 있어 서명 검증만 가능하며, 발급(서명)은 벤더가 보관한
 * 비공개 키로만 할 수 있다({@link com.company.leave.license.tool.LicenseTool}).</p>
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    /**
     * 앱 내장 공개 키(Base64, X.509).  LicenseTool keygen 으로 생성한 PUBLIC KEY 로 교체할 것.
     */
    private static final String PUBLIC_KEY_B64 =
            "MCowBQYDK2VwAyEAVSZEuH/Y2tdZfiF+p5tB1syp1BPUFghn4cxdiL6LeT4=";

    private final ObjectMapper objectMapper;
    private final String licenseKey;
    private final String expectedHost;

    private volatile LicenseStatus status;

    public LicenseService(ObjectMapper objectMapper,
                          @Value("${app.license.key:}") String licenseKey,
                          @Value("${app.license.host:}") String expectedHost) {
        this.objectMapper = objectMapper;
        this.licenseKey = licenseKey;
        this.expectedHost = expectedHost;
    }

    @PostConstruct
    void init() {
        this.status = evaluate();
        if (status.valid()) {
            log.info("[LICENSE] 유효 · 사용처={} · 만료={} · 최대사용자={}",
                    status.licensee(), status.expiresAt(), status.maxUsers());
        } else {
            log.error("[LICENSE] 유효하지 않음(모든 기능 차단됨): {}", status.reason());
        }
    }

    public boolean valid() {
        return status != null && status.valid();
    }

    public LicenseStatus status() {
        return status != null ? status : evaluate();
    }

    /** 사용자 정원 검사. 유효하며 상한이 있으면 초과 시 예외. */
    public void checkUserQuota(long currentActiveUsers) {
        if (status == null || !status.valid()) {
            return;
        }
        int max = status.maxUsers();
        if (max > 0 && currentActiveUsers >= max) {
            throw new BusinessException(ErrorCode.LICENSE_USER_LIMIT,
                    "라이선스 최대 사용자 수(" + max + "명)에 도달했습니다.");
        }
    }

    private LicenseStatus evaluate() {
        if (!StringUtils.hasText(licenseKey)) {
            return LicenseStatus.invalid("라이선스 키가 설정되지 않았습니다.");
        }
        try {
            String[] parts = licenseKey.trim().split("\\.");
            if (parts.length != 2) {
                return LicenseStatus.invalid("라이선스 형식 오류");
            }
            byte[] payloadBytes = LicenseCrypto.b64urlDecode(parts[0]);
            byte[] sig = LicenseCrypto.b64urlDecode(parts[1]);

            PublicKey pub = LicenseCrypto.publicKeyFromBase64(PUBLIC_KEY_B64);
            if (!LicenseCrypto.verify(pub, payloadBytes, sig)) {
                return LicenseStatus.invalid("서명 검증 실패(위조 또는 손상)");
            }

            LicensePayload p = objectMapper.readValue(payloadBytes, LicensePayload.class);

            LocalDate today = LocalDate.now();
            if (p.expiresAt() != null && today.isAfter(p.expiresAt())) {
                return LicenseStatus.expiredOf(p);
            }
            if (StringUtils.hasText(expectedHost) && StringUtils.hasText(p.host())
                    && !expectedHost.trim().equalsIgnoreCase(p.host().trim())) {
                return LicenseStatus.invalidOf(p, "설치처(host) 불일치");
            }

            long daysLeft = p.expiresAt() != null
                    ? ChronoUnit.DAYS.between(today, p.expiresAt()) : Long.MAX_VALUE;
            return LicenseStatus.valid(p, daysLeft);
        } catch (Exception e) {
            return LicenseStatus.invalid("라이선스 파싱 오류: " + e.getMessage());
        }
    }

    /** 라이선스 상태 스냅샷. */
    public record LicenseStatus(
            boolean enforced,
            boolean valid,
            String reason,
            String licensee,
            LocalDate expiresAt,
            long daysLeft,
            int maxUsers) {

        static LicenseStatus valid(LicensePayload p, long daysLeft) {
            return new LicenseStatus(true, true, "OK", p.licensee(), p.expiresAt(), daysLeft, p.maxUsers());
        }

        static LicenseStatus expiredOf(LicensePayload p) {
            return new LicenseStatus(true, false, "라이선스가 만료되었습니다.",
                    p.licensee(), p.expiresAt(), 0, p.maxUsers());
        }

        static LicenseStatus invalidOf(LicensePayload p, String reason) {
            return new LicenseStatus(true, false, reason, p.licensee(), p.expiresAt(), 0, p.maxUsers());
        }

        static LicenseStatus invalid(String reason) {
            return new LicenseStatus(true, false, reason, null, null, 0, 0);
        }
    }
}
