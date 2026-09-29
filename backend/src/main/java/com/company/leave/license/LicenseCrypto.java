package com.company.leave.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 서명 유틸 (JDK 내장). 라이선스 발급 도구와 앱 검증이 공유.
 * 외부 의존성 없음(발급 도구를 단독 실행할 수 있도록).
 *
 * <p>라이선스 토큰 형식: {@code base64url(payloadJson) + "." + base64url(signature)}</p>
 */
public final class LicenseCrypto {

    private static final String ALG = "Ed25519";

    private LicenseCrypto() {
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data) {
        try {
            Signature s = Signature.getInstance(ALG);
            s.initSign(privateKey);
            s.update(data);
            return s.sign();
        } catch (Exception e) {
            throw new IllegalStateException("서명 실패: " + e.getMessage(), e);
        }
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature s = Signature.getInstance(ALG);
            s.initVerify(publicKey);
            s.update(data);
            return s.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static PublicKey publicKeyFromBase64(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALG).generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("공개키 로드 실패: " + e.getMessage(), e);
        }
    }

    public static PrivateKey privateKeyFromBase64(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALG).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("비공개키 로드 실패: " + e.getMessage(), e);
        }
    }

    public static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] b64urlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
