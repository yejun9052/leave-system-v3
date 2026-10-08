package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.company.leave.audit.AuditService;
import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupService.DbTarget;
import com.company.leave.backup.RestoreService.Source;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL(임시 컨테이너)과 실제 pg_dump·pg_restore·psql 로 백업·복원을 확인한다.
 * Docker 가 없거나 PostgreSQL 16 클라이언트 도구가 없으면(BACKUP_PG_BIN, C:/tool/pgsql/bin, PATH 순) 건너뛴다.
 */
@DisplayName("복원(실제 PostgreSQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@Testcontainers(disabledWithoutDocker = true)
class RestoreIntegrationTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:16-alpine");

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private JdbcTemplate jdbc;
    private Flyway flyway;
    private BackupService backups;
    private RestoreService restore;
    private MaintenanceMode maintenance;

    /** pg 도구 폴더(비어 있으면 PATH). 실행해 보고 없으면 null. */
    private static String pgBin() {
        String env = System.getenv("BACKUP_PG_BIN");
        for (String candidate : Stream.of(env, "C:/tool/pgsql/bin", "").filter(c -> c != null).toList()) {
            try {
                BackupProcessRunner.Result r = new BackupProcessRunner().run(
                        List.of(new BackupProperties("x", candidate).tool("pg_restore"), "--version"), Map.of(),
                        Duration.ofSeconds(20));
                if (r.exitCode() == 0 && r.output().contains(" 16.")) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // 다음 후보
            }
        }
        return null;
    }

    @BeforeEach
    void setUp() {
        String bin = pgBin();
        assumeTrue(bin != null, "PostgreSQL 16 클라이언트 도구(pg_dump·pg_restore·psql)가 없어 건너뜀");
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        jdbc.execute("DROP ROLE IF EXISTS restore_probe");
        // 백업은 V8 시절에 만든다(복원한 뒤 지금 버전(V10)까지 다시 올라가는지 보려고)
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("8").load().migrate();
        flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        FlywayDbVersion version = new FlywayDbVersion(flyway);
        maintenance = new MaintenanceMode();
        backups = new BackupService(new BackupProperties(dir.toString(), bin), new BackupProcessRunner(),
                new DbTarget(PG.getHost(), PG.getMappedPort(5432), PG.getDatabaseName(), PG.getUsername(),
                        PG.getPassword()), Clock.system(ZoneId.of("Asia/Seoul")), version, JSON);
        restore = new RestoreService(backups, maintenance, version, version, jdbc, ds, mock(AuditService.class),
                mock(BackupMessenger.class), mock(ApplicationEventPublisher.class));
        jdbc.update("INSERT INTO blackout_periods (start_date, end_date, name) VALUES ('2026-12-24', '2026-12-31', '원래 데이터')");
    }

    private List<String> blackoutNames() {
        return jdbc.queryForList("SELECT name FROM blackout_periods ORDER BY id", String.class);
    }

    private long count(Kind kind) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith("_" + kind.code() + ".dump")).count();
        }
    }

    @Test
    void 데이터를_바꾼_뒤_복원하면_원래대로_돌아오고_낮은_버전_백업은_지금_버전으로_올린다() throws Exception {
        BackupFile backup = backups.backup(Kind.MANUAL);
        assertThat(backups.readInfo(dir.resolve(backup.fileName())).orElseThrow().dbVersion()).isEqualTo("8");
        // 앱을 업데이트(V9·V10)하고 데이터를 바꾸고 로그인 세션이 생김
        flyway.migrate();
        jdbc.update("DELETE FROM blackout_periods");
        jdbc.update("INSERT INTO blackout_periods (start_date, end_date, name) VALUES ('2027-01-01', '2027-01-02', '바뀐 데이터')");
        jdbc.update("UPDATE backup_settings SET enabled = false");
        jdbc.update("INSERT INTO spring_session VALUES ('p1', 's1', 0, 0, 1800, 9999999999999, 'admin')");

        RestoreService.Result result = restore.restore(backup.fileName(), Source.BACKUP, 1L, "관리자");

        assertThat(blackoutNames()).containsExactly("원래 데이터");
        assertThat(result.fromVersion()).isEqualTo("8");
        assertThat(result.toVersion()).isEqualTo("10");
        assertThat(result.migrationsApplied()).isEqualTo(2);
        // 백업에 없던 V9 표(V10 에서 보관 기간 칸으로 바뀜)는 지워졌다가 마이그레이션으로 기본값과 함께 다시 생김
        assertThat(jdbc.queryForObject("SELECT enabled FROM backup_settings", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT keep_months FROM backup_settings", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
        assertThat(count(Kind.PRE_RESTORE)).isEqualTo(1);
        assertThat(maintenance.isActive()).isFalse();
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
        }
    }

    @Test
    void V10_은_저장된_월간_보관_개수를_보관_기간으로_옮기고_예전_칸을_지운다() {
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").target("9").load().migrate();
        jdbc.update("UPDATE backup_settings SET keep_monthly = 12");

        flyway.migrate();

        assertThat(jdbc.queryForObject("SELECT keep_months FROM backup_settings", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'backup_settings'", String.class))
                .contains("keep_months").doesNotContain("keep_daily", "keep_weekly", "keep_monthly");
    }

    @Test
    void 복원_전_백업으로_다시_복원하면_복원하기_직전_상태로_돌아온다() {
        flyway.migrate();
        BackupFile backup = backups.backup(Kind.MANUAL);
        jdbc.update("INSERT INTO blackout_periods (start_date, end_date, name) VALUES ('2027-01-01', '2027-01-02', '복원 직전 데이터')");

        RestoreService.Result first = restore.restore(backup.fileName(), Source.BACKUP, 1L, "관리자");
        assertThat(blackoutNames()).containsExactly("원래 데이터");
        restore.restore(first.preRestoreFile(), Source.BACKUP, 1L, "관리자");

        assertThat(blackoutNames()).containsExactly("원래 데이터", "복원 직전 데이터");
    }

    @Test
    void 체크섬이_다르거나_더_새_버전이거나_깨진_파일이면_거부하고_DB는_그대로다() throws Exception {
        flyway.migrate();
        BackupFile backup = backups.backup(Kind.MANUAL);
        jdbc.update("INSERT INTO blackout_periods (start_date, end_date, name) VALUES ('2027-01-01', '2027-01-02', '나중 데이터')");
        Path info = BackupService.infoPath(dir.resolve(backup.fileName()));
        BackupInfo original = JSON.readValue(Files.readString(info), BackupInfo.class);

        Files.writeString(info, JSON.writeValueAsString(new BackupInfo(original.fileName(), original.createdAt(),
                original.kind(), original.dbVersion(), original.size(), "0".repeat(64))));
        assertThatThrownBy(() -> restore.restore(backup.fileName(), Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_CHECKSUM_MISMATCH));

        Files.writeString(info, JSON.writeValueAsString(new BackupInfo(original.fileName(), original.createdAt(),
                original.kind(), "99", original.size(), original.sha256())));
        assertThatThrownBy(() -> restore.restore(backup.fileName(), Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_NEWER_VERSION));

        Files.writeString(dir.resolve("import").resolve("broken.dump"), "이건 백업 파일이 아님");
        assertThatThrownBy(() -> restore.restore("broken.dump", Source.IMPORT, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_INVALID_FILE));

        assertThat(blackoutNames()).containsExactly("원래 데이터", "나중 데이터");
        assertThat(count(Kind.PRE_RESTORE)).isZero();
    }

    @Test
    void 정보_파일이_없는_백업은_백업_안의_기록으로_버전을_확인한다() throws Exception {
        BackupFile backup = backups.backup(Kind.MANUAL);
        flyway.migrate();
        Path imported = dir.resolve("import").resolve("from-old-server.dump");
        Files.copy(dir.resolve(backup.fileName()), imported);

        RestoreService.Check check = restore.check("from-old-server.dump", Source.IMPORT);

        assertThat(check.dbVersion()).isEqualTo("8");
        assertThat(check.currentVersion()).isEqualTo("10");
        assertThat(check.checksumVerified()).isFalse();
        assertThat(check.kind()).isEqualTo(Kind.IMPORTED);
    }

    @Test
    void 복원_중간에_실패하면_한_트랜잭션이라_DB는_그대로다() throws Exception {
        flyway.migrate();
        // 백업 안에 "이 역할에게 권한 주기"가 들어가게 한 뒤 역할을 지운다 → 복원 끝부분(권한)에서 실패
        jdbc.execute("CREATE ROLE restore_probe");
        jdbc.execute("GRANT SELECT ON blackout_periods TO restore_probe");
        BackupFile backup = backups.backup(Kind.MANUAL);
        jdbc.execute("REVOKE ALL ON blackout_periods FROM restore_probe");
        jdbc.execute("DROP ROLE restore_probe");
        jdbc.update("INSERT INTO blackout_periods (start_date, end_date, name) VALUES ('2027-01-01', '2027-01-02', '나중 데이터')");

        assertThatThrownBy(() -> restore.restore(backup.fileName(), Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_RESTORE_FAILED);
                    assertThat(e.getMessage()).contains("_pre-restore.dump").contains("되돌릴 수 있습니다");
                });

        assertThat(blackoutNames()).containsExactly("원래 데이터", "나중 데이터");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM backup_settings", Integer.class)).isEqualTo(1);
        assertThat(maintenance.isActive()).isFalse();
        assertThat(count(Kind.PRE_RESTORE)).isEqualTo(1);
    }
}
