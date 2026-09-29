package com.company.leave.license.tool;

import com.company.leave.license.LicenseCrypto;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * 라이선스 발급 도구 (벤더 전용, 서버에 배포하지 않음).
 * 외부 의존성 없이 컴파일된 클래스만으로 실행 가능.
 *
 * <pre>
 * 1) 키 쌍 생성(최초 1회):
 *    java -cp build/classes/java/main com.company.leave.license.tool.LicenseTool keygen
 *    → PUBLIC_KEY 를 LicenseService 상수에 넣고, PRIVATE_KEY 는 안전하게 보관
 *
 * 2) 라이선스 발급(고객마다):
 *    java -cp build/classes/java/main com.company.leave.license.tool.LicenseTool \
 *         issue &lt;privateKeyB64&gt; "회사명" 2027-12-31 50 leave.company.com
 *    → 출력된 라이선스 문자열을 고객 서버의 LICENSE_KEY 로 설정
 * </pre>
 */
public final class LicenseTool {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "keygen" -> keygen();
            case "issue" -> issue(args);
            default -> usage();
        }
    }

    private static void keygen() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        System.out.println("=== PUBLIC KEY (앱 LicenseService 상수에 삽입) ===");
        System.out.println(pub);
        System.out.println();
        System.out.println("=== PRIVATE KEY (절대 노출 금지, 발급용으로만 보관) ===");
        System.out.println(priv);
    }

    private static void issue(String[] args) {
        if (args.length < 6) {
            System.out.println("usage: issue <privateKeyB64> <licensee> <expiresAt yyyy-MM-dd> <maxUsers> <host>");
            return;
        }
        String privB64 = args[1];
        String licensee = args[2];
        LocalDate expiresAt = LocalDate.parse(args[3]);
        int maxUsers = Integer.parseInt(args[4]);
        String host = args[5];
        String id = UUID.randomUUID().toString();
        LocalDate issuedAt = LocalDate.now();

        String payloadJson = "{"
                + "\"id\":\"" + esc(id) + "\","
                + "\"licensee\":\"" + esc(licensee) + "\","
                + "\"issuedAt\":\"" + issuedAt + "\","
                + "\"expiresAt\":\"" + expiresAt + "\","
                + "\"maxUsers\":" + maxUsers + ","
                + "\"host\":\"" + esc(host) + "\""
                + "}";

        byte[] payloadBytes = LicenseCrypto.utf8(payloadJson);
        byte[] sig = LicenseCrypto.sign(LicenseCrypto.privateKeyFromBase64(privB64), payloadBytes);
        String token = LicenseCrypto.b64url(payloadBytes) + "." + LicenseCrypto.b64url(sig);

        System.out.println("=== LICENSE (고객 서버 LICENSE_KEY 로 설정) ===");
        System.out.println(token);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void usage() {
        System.out.println("commands: keygen | issue <privateKeyB64> <licensee> <expiresAt> <maxUsers> <host>");
    }
}
