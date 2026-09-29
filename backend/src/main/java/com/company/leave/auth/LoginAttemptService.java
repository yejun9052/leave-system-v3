package com.company.leave.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 로그인 브루트포스 방어(인메모리).
 * 이메일 단위로 연속 실패 횟수를 세고, 임계치 초과 시 일정 시간 잠근다.
 * 단일 인스턴스 배포 기준이며, 다중 인스턴스 확장 시 외부 저장소(Redis 등)로 교체한다.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempt(int count, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** 현재 잠금 상태면 true. */
    public boolean isBlocked(String email) {
        String k = key(email);
        Attempt a = attempts.get(k);
        if (a == null || a.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(a.lockedUntil())) {
            attempts.remove(k, a); // 값 조건부 제거: 그 사이 새로 쌓인 실패 카운트는 보존(레이스 방지)
            return false;
        }
        return true;
    }

    /** 로그인 실패 기록. 임계치 도달 시 잠금 설정. */
    public void loginFailed(String email) {
        String k = key(email);
        attempts.compute(k, (ignored, prev) -> {
            int count = (prev == null ? 0 : prev.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    /** 로그인 성공 시 카운터 초기화. */
    public void loginSucceeded(String email) {
        attempts.remove(key(email));
    }
}
