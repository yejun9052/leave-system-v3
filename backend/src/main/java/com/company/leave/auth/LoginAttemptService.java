package com.company.leave.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 로그인 브루트포스 방어(인메모리).
 * 접속한 PC(IP) 단위로 연속 실패 횟수를 세고, 10번 실패하면 그 IP의 로그인 시도를 15분 동안 막는다.
 * <ul>
 *   <li>계정은 잠그지 않는다: 누가 남의 아이디(예: admin)로 일부러 틀려도 본인은 자기 PC에서 로그인할 수 있다</li>
 *   <li>여러 아이디에 나눠 시도해도 같은 IP면 합쳐서 센다</li>
 *   <li>로그인에 성공하면 그 아이디로 틀린 횟수만 지운다. 다른 아이디로 틀린 횟수는 남는다
 *       (남의 비밀번호를 9번 틀리고 자기 계정으로 로그인해 횟수를 지우는 식으로 제한을 피하지 못하게)</li>
 *   <li>로그인만 막고, 같은 IP에서 이미 로그인한 사람은 계속 쓴다</li>
 *   <li>마지막 실패 뒤 15분 동안 더 틀리지 않으면 실패 횟수를 잊는다(몇 주에 걸친 실수가 쌓여 막히지 않게)</li>
 *   <li>시도는 비밀번호를 비교하기 <b>전에</b> 실패로 먼저 세고({@link #tryAcquire}), 맞으면 되돌린다. 비교 뒤에 세면
 *       한꺼번에 보낸 요청이 모두 "아직 안 막힘"을 보고 들어와 10번을 훨씬 넘게 맞혀 볼 수 있다</li>
 *   <li>막힌 동안 들어온 요청은 처음 한 번만 "기록하라"고 알려 준다(이벤트 로그를 채우거나 DB 를 쓰게 만들지 못하게)</li>
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

    /**
     * @param lastFailure 마지막 실패(시도) 시각 @param lockedUntil 막힌 동안이면 풀리는 시각, 아니면 null
     * @param byLoginId 아이디별 틀린 횟수(합이 count). 성공한 아이디의 몫만 빼기 위해 둔다
     * @param blockNoticed 이번 차단 기간에 막힌 요청을 이미 한 번 알렸는지(감사 기록은 한 번만)
     */
    private record Attempt(int count, Instant lastFailure, Instant lockedUntil, Map<String, Integer> byLoginId,
                           boolean blockNoticed) {
    }

    /**
     * 막혀서 이번 시도를 받지 않았다.
     * @param remaining 풀릴 때까지 남은 시간 @param firstInLock 이번 차단 기간에 처음 막힌 요청이면 true(이때만 감사 기록)
     */
    public record Block(Duration remaining, boolean firstInLock) {
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

    /** 아이디는 앞뒤 공백·대소문자를 무시하고 센다 */
    private static String loginKey(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
    }

    /** 이 IP의 로그인 시도가 막혀 있으면 남은 시간, 아니면 비어 있음(조회만, 세지 않음). */
    public Optional<Duration> lockRemaining(String clientIp) {
        Attempt a = attempts.get(key(clientIp));
        Instant now = clock.instant();
        return locked(a, now) ? Optional.of(Duration.between(now, a.lockedUntil())) : Optional.empty();
    }

    /** 현재 막혀 있으면 true. */
    public boolean isBlocked(String clientIp) {
        return lockRemaining(clientIp).isPresent();
    }

    /**
     * 로그인 시도 한 번(loginId: 입력한 아이디). 비밀번호를 비교하기 전에 부른다.
     * 막혀 있으면 이번 시도는 세지 않고 {@link Block} 을 돌려준다. 아니면 이번 시도를 실패로 미리 세고(10번째면 막음)
     * 비어 있음을 돌려준다. 맞으면 {@link #loginSucceeded} 가 되돌린다.
     * 확인과 세기를 한 번에 하므로 한꺼번에 보낸 요청도 정확히 10번까지만 들어간다.
     */
    public Optional<Block> tryAcquire(String clientIp, String loginId) {
        Instant now = clock.instant();
        if (attempts.size() >= CLEANUP_THRESHOLD) {
            attempts.values().removeIf(a -> forgettable(a, now));
        }
        Block[] blocked = new Block[1];
        attempts.compute(key(clientIp), (ignored, prev) -> {
            if (locked(prev, now)) {
                blocked[0] = new Block(Duration.between(now, prev.lockedUntil()), !prev.blockNoticed());
                return prev.blockNoticed() ? prev
                        : new Attempt(prev.count(), prev.lastFailure(), prev.lockedUntil(), prev.byLoginId(), true);
            }
            boolean fresh = prev == null || forgettable(prev, now);
            int count = (fresh ? 0 : prev.count()) + 1;
            Map<String, Integer> byLoginId = new HashMap<>(fresh ? Map.of() : prev.byLoginId());
            byLoginId.merge(loginKey(loginId), 1, Integer::sum);
            Instant lockedUntil = count >= MAX_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
            return new Attempt(count, now, lockedUntil, Map.copyOf(byLoginId), false);
        });
        return Optional.ofNullable(blocked[0]);
    }

    /**
     * 로그인 성공: 그 IP에서 이 아이디로 센 횟수(이번 시도 포함)를 지운다. 다른 아이디로 틀린 횟수는 남는다.
     * 이번 시도가 10번째라 막혔던 것이면 남은 횟수가 10번 아래로 내려가 풀린다.
     */
    public void loginSucceeded(String clientIp, String loginId) {
        String id = loginKey(loginId);
        attempts.computeIfPresent(key(clientIp), (ignored, prev) -> {
            Integer mine = prev.byLoginId().get(id);
            if (mine == null) {
                return prev;
            }
            int count = prev.count() - mine;
            if (count <= 0) {
                return null; // 남은 실패가 없으면 기록을 지운다
            }
            Map<String, Integer> byLoginId = new HashMap<>(prev.byLoginId());
            byLoginId.remove(id);
            Instant lockedUntil = count >= MAX_ATTEMPTS ? prev.lockedUntil() : null;
            return new Attempt(count, prev.lastFailure(), lockedUntil, Map.copyOf(byLoginId),
                    lockedUntil != null && prev.blockNoticed());
        });
    }

    private static boolean locked(Attempt a, Instant now) {
        return a != null && a.lockedUntil() != null && !now.isAfter(a.lockedUntil());
    }

    /** 막혀 있지 않고 마지막 실패가 15분보다 오래됐으면 잊어도 된다. */
    private static boolean forgettable(Attempt a, Instant now) {
        return !locked(a, now) && a.lastFailure().plus(LOCK_DURATION).isBefore(now);
    }
}
