package com.company.leave.realtime;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 갱신 연결(SSE) 모음. 화면은 로그인하면 {@code GET /api/realtime} 으로 연결해 두고,
 * 데이터가 바뀌면 "바뀌었다"는 신호만 받아 지금 보고 있는 데이터를 기존 API 로 다시 불러온다.
 * 신호에는 데이터가 없으므로 권한 밖의 내용이 새지 않는다.
 *
 * <ul>
 *   <li>{@link #broadcast}: 모두에게(휴가·직원·정책 등 여럿이 보는 데이터)</li>
 *   <li>{@link #sendTo}: 한 사람에게만(내 알림 읽음 처리처럼 나만 보는 데이터 — 내 다른 탭·기기)</li>
 * </ul>
 * 보내기는 전용 스레드 하나에서 한다: 요청 응답을 늦추지 않고, 한 연결에 동시에 쓰지 않는다.
 * 연결은 {@link #TIMEOUT} 마다 끊고 브라우저가 다시 연결한다(그때 세션 사용 시각이 갱신된다).
 * 서버를 끌 때는 웹 서버가 진행 중인 요청을 기다리기 전에 먼저 연결을 닫는다(SmartLifecycle, 가장 먼저 멈춤):
 * 열린 탭 때문에 종료가 늦어져 컨테이너가 강제 종료되지 않게.
 */
@Component
public class RealtimeHub implements SmartLifecycle {

    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final Duration HEARTBEAT = Duration.ofSeconds(25);
    /** 끊겼을 때 브라우저가 다시 연결하기까지 기다리는 시간 */
    static final Duration RECONNECT = Duration.ofSeconds(3);

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();
    private volatile boolean running;
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "realtime-sender");
        t.setDaemon(true);
        return t;
    });

    public RealtimeHub() {
        // 프록시(Caddy)·브라우저가 조용한 연결을 끊지 않게 주석 한 줄씩 보낸다
        sender.scheduleAtFixedRate(this::heartbeat, HEARTBEAT.toSeconds(), HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
    }

    /** 이 직원의 새 연결. 연결되면 바로 "connected" 를 보내 응답을 시작한다. */
    public SseEmitter connect(Long employeeId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        // 추가도 compute 안에서: 같은 직원의 다른 연결이 끊기며 빈 묶음을 지우는 것과 겹쳐도 새 연결이 빠지지 않게
        connections.compute(employeeId, (id, set) -> {
            Set<SseEmitter> next = set != null ? set : ConcurrentHashMap.newKeySet();
            next.add(emitter);
            return next;
        });
        Runnable remove = () -> remove(employeeId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(e -> remove.run());
        submit(() -> deliver(employeeId, emitter,
                SseEmitter.event().name("connected").data("").reconnectTime(RECONNECT.toMillis())));
        return emitter;
    }

    /**
     * 모두에게 "바뀜" 신호.
     *
     * @param origin 바꾼 화면(탭)의 ID. 그 탭은 이미 스스로 다시 불러왔으므로 신호를 무시한다. 자동 작업이면 null
     */
    public void broadcast(String origin) {
        String data = payload(origin);
        submit(() -> connections.forEach((id, set) -> set.forEach(e -> deliver(id, e, change(data)))));
    }

    /** 한 사람(의 모든 탭·기기)에게만 "바뀜" 신호. */
    public void sendTo(Long employeeId, String origin) {
        String data = payload(origin);
        submit(() -> connections.getOrDefault(employeeId, Set.of())
                .forEach(e -> deliver(employeeId, e, change(data))));
    }

    /** 지금 연결 수(점검·테스트용). */
    public int connectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public void start() {
        running = true;
    }

    /** 서버 종료: 웹 서버의 정상 종료 대기보다 먼저 열린 연결을 모두 닫는다. */
    @Override
    public void stop() {
        running = false;
        closeAll();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
        closeAll();
    }

    private void closeAll() {
        connections.values().forEach(set -> set.forEach(e -> {
            try {
                e.complete();
            } catch (RuntimeException ignored) {
                // 이미 끝난 연결
            }
        }));
        connections.clear();
    }

    /** 보내기 전용 스레드에 맡긴다. 종료 중이라 받지 않으면 버린다(신호는 다음 연결 때 새로 불러오면 된다). */
    private void submit(Runnable task) {
        try {
            sender.execute(task);
        } catch (RejectedExecutionException ex) {
            log.debug("실시간 신호를 보내지 않음(종료 중)");
        }
    }

    private void heartbeat() {
        connections.forEach((id, set) -> set.forEach(e -> deliver(id, e, SseEmitter.event().comment("ping"))));
    }

    private static SseEmitter.SseEventBuilder change(String data) {
        return SseEmitter.event().name("change").data(data);
    }

    /** {"origin":"탭 ID"} — 탭 ID 는 화면이 만든 영문·숫자만 받는다. */
    static String payload(String origin) {
        String safe = origin != null && origin.matches("[A-Za-z0-9-]{1,64}") ? origin : "";
        return "{\"origin\":\"" + safe + "\"}";
    }

    private void deliver(Long employeeId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            // 브라우저가 닫혔거나 이미 끝난 연결
            log.debug("실시간 연결 정리(직원 {}): {}", employeeId, ex.getMessage());
            remove(employeeId, emitter);
        }
    }

    private void remove(Long employeeId, SseEmitter emitter) {
        connections.computeIfPresent(employeeId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
