package com.company.leave.auth.password;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 요청 횟수 제한(인메모리): 이메일당 1시간에 3회.
 * 단일 인스턴스 배포 기준(LoginAttemptService 와 같은 전제).
 */
@Component
public class ResetRequestLimiter {

    static final int MAX_REQUESTS = 3;
    static final Duration WINDOW = Duration.ofHours(1);
    /** 존재하지 않는 이메일을 대량으로 넣어 메모리를 늘리는 것을 막기 위한 정리 기준. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public ResetRequestLimiter() {
        this(Clock.systemUTC());
    }

    ResetRequestLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 이번 요청을 허용하면 true(허용된 요청만 횟수에 포함). */
    public boolean tryAcquire(String email) {
        Instant now = clock.instant();
        if (requests.size() > CLEANUP_THRESHOLD) {
            requests.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now);
                    return e.getValue().isEmpty();
                }
            });
        }
        Deque<Instant> times = requests.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            if (times.size() >= MAX_REQUESTS) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    private void prune(Deque<Instant> times, Instant now) {
        Instant limit = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(limit)) {
            times.pollFirst();
        }
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
