package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupDtos.Overview;
import com.company.leave.backup.BackupService.DbTarget;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 백업: 파일 이름 검증, pg_dump 실행·실패 처리, 동시 실행 거부, 목록. pg_dump 는 가짜로 바꿔 실행하지 않는다. */
@DisplayName("백업 서비스")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BackupServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 10, 8, 9, 30, 0, 0, SEOUL).toInstant(), SEOUL);
    private static final DbTarget DB = new DbTarget("localhost", 5432, "annual_leave", "leave", "secret-pw");
    private static final String NAME = "annual_leave_20261008_093000_manual.dump";

    @TempDir
    Path dir;

    /** 가짜 pg_dump: -f 로 받은 파일에 내용을 쓰고 정해진 종료 코드를 돌려준다. */
    private static class FakeRunner extends BackupProcessRunner {
        final int exitCode;
        final String output;
        List<String> command;
        Map<String, String> env;

        FakeRunner(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        @Override
        public Result run(List<String> command, Map<String, String> env, Duration timeout)
                throws IOException, InterruptedException, TimeoutException {
            this.command = command;
            this.env = env;
            beforeWrite();
            Files.writeString(Path.of(command.get(command.indexOf("-f") + 1)), "PGDMP");
            return new Result(exitCode, output);
        }

        void beforeWrite() throws InterruptedException {
        }
    }

    private BackupService service(BackupProcessRunner runner) {
        return new BackupService(new BackupProperties(dir.toString(), ""), runner, DB, CLOCK);
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        }
    }

    @Test
    void 정상_이름은_백업_폴더_안의_파일로_찾는다() throws IOException {
        Files.writeString(dir.resolve(NAME), "PGDMP");

        assertThat(service(new FakeRunner(0, "")).resolve(NAME)).isEqualTo(dir.resolve(NAME).toAbsolutePath());
    }

    @Test
    void 경로를_벗어나거나_형식이_다른_이름은_거부한다() {
        BackupService service = service(new FakeRunner(0, ""));
        for (String bad : List.of("../x.dump", "..\\x.dump", "x.dump", "annual_leave_20261008_093000_manual.sql",
                "annual_leave_20261008_093000_manual.dump.tmp", "sub/" + NAME, "../" + NAME,
                "annual_leave_20261008_093000_other.dump", "")) {
            assertThatThrownBy(() -> service.resolve(bad))
                    .as(bad)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_INVALID_NAME));
        }
    }

    @Test
    void 형식은_맞지만_없는_파일은_찾을_수_없다() {
        assertThatThrownBy(() -> service(new FakeRunner(0, "")).resolve(NAME))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_NOT_FOUND));
    }

    @Test
    void 백업하면_임시_이름으로_쓴_뒤_정해진_이름으로_바꾼다() throws IOException {
        FakeRunner runner = new FakeRunner(0, "");

        BackupFile file = service(runner).backup(Kind.MANUAL);

        assertThat(file.fileName()).isEqualTo(NAME);
        assertThat(file.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 9, 30, 0));
        assertThat(file.kind()).isEqualTo(Kind.MANUAL);
        assertThat(file.size()).isEqualTo(5);
        assertThat(namesIn(dir)).containsExactlyInAnyOrder(NAME, BackupService.IMPORT_DIR);
        assertThat(runner.command.get(runner.command.indexOf("-f") + 1)).endsWith(NAME + ".tmp");
    }

    @Test
    void 세션_데이터는_빼고_비밀번호는_명령_인자가_아닌_환경변수로_넘긴다() {
        FakeRunner runner = new FakeRunner(0, "");

        service(runner).backup(Kind.MANUAL);

        assertThat(runner.command).contains("-Fc", "-w",
                "--exclude-table-data=spring_session", "--exclude-table-data=spring_session_attributes",
                "-h", "localhost", "-p", "5432", "-U", "leave", "annual_leave");
        assertThat(runner.command).noneMatch(arg -> arg.contains("secret-pw"));
        assertThat(runner.env).containsEntry("PGPASSWORD", "secret-pw");
    }

    @Test
    void pg_dump가_실패하면_예외를_던지고_만들다_만_파일을_지운다() throws IOException {
        FakeRunner runner = new FakeRunner(1, "pg_dump: error: connection failed\n");

        assertThatThrownBy(() -> service(runner).backup(Kind.MANUAL))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_FAILED);
                    assertThat(e.getMessage()).contains("종료 코드 1").contains("connection failed");
                });
        assertThat(namesIn(dir)).containsExactly(BackupService.IMPORT_DIR);
    }

    @Test
    void pg_dump를_실행할_수_없으면_실패로_알린다() throws IOException {
        BackupProcessRunner missing = new BackupProcessRunner() {
            @Override
            public Result run(List<String> command, Map<String, String> env, Duration timeout) throws IOException {
                throw new IOException("Cannot run program \"pg_dump\"");
            }
        };

        assertThatThrownBy(() -> service(missing).backup(Kind.MANUAL))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_FAILED);
                    assertThat(e.getMessage()).contains("app.backup.pg-bin");
                });
        assertThat(namesIn(dir)).containsExactly(BackupService.IMPORT_DIR);
    }

    @Test
    void 제한_시간을_넘기면_실패로_알리고_임시_파일을_지운다() throws IOException {
        FakeRunner slow = new FakeRunner(0, "") {
            @Override
            public Result run(List<String> command, Map<String, String> env, Duration timeout)
                    throws IOException, TimeoutException {
                Files.writeString(Path.of(command.get(command.indexOf("-f") + 1)), "PG");
                throw new TimeoutException();
            }
        };

        assertThatThrownBy(() -> service(slow).backup(Kind.MANUAL))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getMessage()).contains("10분"));
        assertThat(namesIn(dir)).containsExactly(BackupService.IMPORT_DIR);
    }

    @Test
    void 백업이_진행_중이면_두_번째_요청은_거부한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeRunner blocking = new FakeRunner(0, "") {
            @Override
            void beforeWrite() throws InterruptedException {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
        };
        BackupService service = service(blocking);

        CompletableFuture<BackupFile> first = CompletableFuture.supplyAsync(() -> service.backup(Kind.MANUAL));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.overview().running()).isTrue();

        assertThatThrownBy(() -> service.backup(Kind.MANUAL))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_IN_PROGRESS));

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).fileName()).isEqualTo(NAME);
        assertThat(service.overview().running()).isFalse();
    }

    @Test
    void 목록은_백업_파일만_최신순으로_보여_주고_폴더가_없으면_만든다() throws IOException {
        Path backups = dir.resolve("backups");
        BackupService service = new BackupService(new BackupProperties(backups.toString(), ""),
                new FakeRunner(0, ""), DB, CLOCK);
        assertThat(service.overview().files()).isEmpty();
        assertThat(backups.resolve(BackupService.IMPORT_DIR)).isDirectory();

        Files.writeString(backups.resolve("annual_leave_20261001_020000_auto.dump"), "a");
        Files.writeString(backups.resolve("annual_leave_20261007_180000_manual.dump"), "bb");
        Files.writeString(backups.resolve("annual_leave_20261008_090000_manual.dump.tmp"), "미완성");
        Files.writeString(backups.resolve("memo.txt"), "기타");
        Files.writeString(backups.resolve(BackupService.IMPORT_DIR).resolve("annual_leave_20260101_000000_manual.dump"),
                "가져올 파일");

        Overview overview = service.overview();

        assertThat(overview.files()).extracting(BackupFile::fileName)
                .containsExactly("annual_leave_20261007_180000_manual.dump", "annual_leave_20261001_020000_auto.dump");
        assertThat(overview.files()).extracting(BackupFile::kind).containsExactly(Kind.MANUAL, Kind.AUTO);
        assertThat(overview.files()).extracting(BackupFile::size).containsExactly(2L, 1L);
        assertThat(overview.dir()).isEqualTo(backups.toAbsolutePath().normalize().toString());
        assertThat(overview.running()).isFalse();
    }

    @Test
    void JDBC_주소에서_호스트_포트_DB_이름을_꺼낸다() {
        assertThat(DbTarget.fromJdbcUrl("jdbc:postgresql://postgres:5432/annual_leave", "u", "p"))
                .isEqualTo(new DbTarget("postgres", 5432, "annual_leave", "u", "p"));
        assertThat(DbTarget.fromJdbcUrl("jdbc:postgresql://db.local/leave?sslmode=disable", "u", "p"))
                .isEqualTo(new DbTarget("db.local", 5432, "leave", "u", "p"));
        assertThatThrownBy(() -> DbTarget.fromJdbcUrl("jdbc:h2:mem:test", "u", "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
