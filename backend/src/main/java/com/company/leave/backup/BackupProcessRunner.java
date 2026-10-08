package com.company.leave.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 외부 명령(pg_dump) 실행. 표준 출력·오류를 함께 읽고, 제한 시간을 넘기면 강제로 끝낸다.
 * 명령 실행만 맡으므로 테스트에서는 가짜로 바꿔 끼운다.
 */
@Component
public class BackupProcessRunner {

    /** 출력은 오류 안내용이라 앞부분만 보관한다. */
    private static final int MAX_OUTPUT = 64 * 1024;

    /**
     * @param exitCode 종료 코드(0 = 성공)
     * @param output   표준 출력·오류 합친 내용(앞부분)
     */
    public record Result(int exitCode, String output) {
    }

    /**
     * @param env 추가 환경변수(비밀번호처럼 명령 인자로 넘기면 안 되는 값)
     * @throws IOException      실행 파일이 없는 등 시작 실패
     * @throws TimeoutException 제한 시간 초과(프로세스는 강제 종료됨)
     */
    public Result run(List<String> command, Map<String, String> env, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().putAll(env);
        Process process = pb.start();
        process.getOutputStream().close(); // 입력을 기다리지 않게
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> drain(process.getInputStream(), out));
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            reader.join(Duration.ofSeconds(5));
            throw new TimeoutException("제한 시간 " + timeout.toMinutes() + "분 초과");
        }
        reader.join(Duration.ofSeconds(5));
        synchronized (out) {
            return new Result(process.exitValue(), out.toString(StandardCharsets.UTF_8));
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[1024];
        try (in) {
            int n;
            while ((n = in.read(buf)) != -1) {
                synchronized (out) {
                    int room = MAX_OUTPUT - out.size();
                    if (room > 0) {
                        out.write(buf, 0, Math.min(n, room));
                    }
                }
            }
        } catch (IOException ignored) {
            // 프로세스가 끝나며 스트림이 닫힌 경우
        }
    }
}
