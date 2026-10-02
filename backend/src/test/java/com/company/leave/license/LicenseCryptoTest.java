package com.company.leave.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Ed25519 서명·검증과 키·Base64URL 변환. 키는 테스트 안에서 새로 만든다(실제 발급 키를 쓰지 않음).
 */
@DisplayName("라이선스 서명 유틸")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LicenseCryptoTest {

    private KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void 비공개_키로_서명한_내용은_짝인_공개_키로_검증된다() {
        byte[] data = LicenseCrypto.utf8("{\"licensee\":\"테스트 회사\"}");

        byte[] sig = LicenseCrypto.sign(keys.getPrivate(), data);

        assertThat(LicenseCrypto.verify(keys.getPublic(), data, sig)).isTrue();
    }

    @Test
    void 내용을_한_글자라도_바꾸면_검증에_실패한다() {
        byte[] sig = LicenseCrypto.sign(keys.getPrivate(), LicenseCrypto.utf8("{\"maxUsers\":10}"));

        assertThat(LicenseCrypto.verify(keys.getPublic(), LicenseCrypto.utf8("{\"maxUsers\":99}"), sig)).isFalse();
    }

    @Test
    void 다른_키로_서명한_내용은_검증에_실패한다() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] data = LicenseCrypto.utf8("payload");

        byte[] sig = LicenseCrypto.sign(other.getPrivate(), data);

        assertThat(LicenseCrypto.verify(keys.getPublic(), data, sig)).isFalse();
    }

    @Test
    void 서명_형식이_깨져_있으면_예외_없이_실패로_본다() {
        assertThat(LicenseCrypto.verify(keys.getPublic(), LicenseCrypto.utf8("payload"), new byte[] {1, 2, 3}))
                .isFalse();
    }

    @Test
    void Base64로_내보낸_키를_다시_읽어_서명과_검증에_쓸_수_있다() {
        String pub = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String priv = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        byte[] data = LicenseCrypto.utf8("payload");

        byte[] sig = LicenseCrypto.sign(LicenseCrypto.privateKeyFromBase64(priv), data);

        assertThat(LicenseCrypto.verify(LicenseCrypto.publicKeyFromBase64(pub), data, sig)).isTrue();
    }

    @Test
    void 잘못된_키_문자열은_읽을_수_없다는_예외를_던진다() {
        assertThatThrownBy(() -> LicenseCrypto.publicKeyFromBase64("bm90LWEta2V5"))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("공개키 로드 실패");
        assertThatThrownBy(() -> LicenseCrypto.privateKeyFromBase64("bm90LWEta2V5"))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("비공개키 로드 실패");
    }

    @Test
    void Base64URL은_패딩_없이_만들고_되돌리면_원래_값이_된다() {
        byte[] data = {(byte) 0xfb, (byte) 0xff, 0x01};

        String encoded = LicenseCrypto.b64url(data);

        assertThat(encoded).doesNotContain("=", "+", "/");
        assertThat(LicenseCrypto.b64urlDecode(encoded)).isEqualTo(data);
    }
}
