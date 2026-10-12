package com.company.leave.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 로그인 브루트포스 방어(인메모리).
 * 접속한 PC(IP) 단위로 연속 실패 횟수를 세고, 10번 실패하면 그 IP의 로그인 시도를 15분 동안 막는다.
 * <ul>
 *   <li>계정은 잠그지 않는다: 누가 남의 아이디(예: admin)로 일부러 틀려도 본인은 자기 PC에서 로그인할 수 있다</li>
 *   <li>여러 아이디에 나눠 시도해도 같은 IP면 합쳐서 센다</li>
 *   <li>로그인만 막고, 같은 IP에서 이미 로그인한 사람은 계속 쓴다</li>
 *   <li>마지막 실패 뒤 15분 동안 더 틀리지 않으면 실패 횟수를 잊는다(몇 주에 걸친 실수가 쌓여 막히지 않게)</li>
 * </ul>
 * IP 는 Caddy 가 넘겨준 X-Forwarded-For 기준(forward-headers-strategy: native → Tomcat RemoteIpValve 가
 * 사설망 프록시가 보낸 값만 믿는다. 클라이언트가 직접 넣은 Forwarded 헤더는 쓰지 않는다).
 * 서버를 재시작하면 기록이 지워진다. 단일 인스턴스 배포 기준이며, 다중 인스턴스 확장 시 외부 저장소(Redis 등)로 교체한다.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 10;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 기록이 이만큼 쌓이면 오래된(잊어도 되는) 기록을 한 번 정리한다 */
    static final int CLEANUP_THRESHOLD = 10_000;

    /** @param lastFailure 마지막 실패 시각 @param lockedUntil 막힌 동안이면 풀리는 시각, 아니면 null */
    private record Attempt(int count, Instant lastFailure, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    private String key(String clientIp) {
        return clientIp == null ? "" : clientIp.trim();
    }

    /** 이 IP의 로그인 시도가 막혀 있으면 남은 시간, 아니면 비어 있음. */
    public Optional<Duration> lockRemaining(String clientIp) {
        String k = key(clientIp);
        Attempt a = attempts.get(k);
        if (a == null || a.lockedUntil() == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (now.isAfter(a.lockedUntil())) {
            attempts.remove(k, a); // 값 조건부 제거: 그 사이 새로 쌓인 실패 카운트는 보존(레이스 방지)
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, a.lockedUntil()));
    }

    /** 현재 막혀 있으면 true. */
    public boolean isBlocked(String clientIp) {
        return lockRemaining(clientIp).isPresent();
    }

    /** 로그인 실패 기록. 임계치에 이르면 그 IP를 막는다. */
    public void loginFailed(String clientIp) {
        Instant now = clock.instant();
        if (attempts.size() >= CLEANUP_THRESHOLD) {
            attempts.values().removeIf(a -> forgettable(a, now));
        }
        attempts.compute(key(clientIp), (ignored, prev) -> {
            int count = (prev == null || forgettable(prev, now) ? 0 : prev.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
            return new Attempt(count, now, lockedUntil);
        });
    }

    /** 로그인 성공 시 그 IP의 실패 기록 초기화. */
    public void loginSucceeded(String clientIp) {
        attempts.remove(key(clientIp));
    }

    /** 막혀 있지 않고 마지막 실패가 15분보다 오래됐으면 잊어도 된다. */
    private static boolean forgettable(Attempt a, Instant now) {
        boolean locked = a.lockedUntil() != null && !now.isAfter(a.lockedUntil());
        return !locked && a.lastFailure().plus(LOCK_DURATION).isBefore(now);
    }
}
