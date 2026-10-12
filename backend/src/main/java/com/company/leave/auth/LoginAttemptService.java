package com.company.leave.auth;

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
 * </ul>
 * IP 는 Caddy 가 넘겨준 X-Forwarded-For 기준(forward-headers-strategy: framework). 서버를 재시작하면 기록이 지워진다.
 * 단일 인스턴스 배포 기준이며, 다중 인스턴스 확장 시 외부 저장소(Redis 등)로 교체한다.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 10;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempt(int count, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

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
        Instant now = Instant.now();
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
        attempts.compute(key(clientIp), (ignored, prev) -> {
            int count = (prev == null ? 0 : prev.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    /** 로그인 성공 시 그 IP의 실패 기록 초기화. */
    public void loginSucceeded(String clientIp) {
        attempts.remove(key(clientIp));
    }
}
