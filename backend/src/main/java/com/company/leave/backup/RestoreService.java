package com.company.leave.backup;

import com.company.leave.audit.AuditService;
import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 백업 복원(데이터 불러오기). 시스템 관리자만, 서버에서 한 번에 처리한다.
 * <ol>
 *   <li>확인: 체크섬(정보 파일이 있으면), pg_restore --list, DB 버전(백업이 더 새 버전이면 거부)</li>
 *   <li>복원 전 백업(pre-restore). 실패하면 복원하지 않는다</li>
 *   <li>점검 모드: 다른 API 는 503, 자동 작업은 건너뜀. 처리 중인 요청이 끝나기를 잠깐 기다린다</li>
 *   <li>한 트랜잭션으로 "모든 표 지우기 → 백업 내용 넣기"(psql --single-transaction). 중간에 실패하면 전부 되돌아가
 *       반만 복원된 상태가 생기지 않는다. pg_restore --clean 은 백업에 없는 표(백업 뒤에 생긴 표)를 남겨 버전이 낮은
 *       백업을 복원한 뒤 마이그레이션이 실패하므로 쓰지 않는다</li>
 *   <li>Flyway migrate 로 지금 앱 버전까지 올림, DB 연결 새로 받기, 모든 로그인 세션 삭제</li>
 *   <li>점검 모드 해제 → 감사 로그(복원으로 되돌아간 뒤 새로 씀, 실패도 기록) → 관리자 알림·메일</li>
 * </ol>
 * 복원할 파일을 읽는 곳은 {@link #openForRestore(Path)} 하나로 모아 두었다(다음 단계 암호화 대비).
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    /** 화면에서 직접 입력해야 하는 확인 문구. */
    public static final String CONFIRM = "복원";
    static final Duration RESTORE_TIMEOUT = Duration.ofMinutes(30);
    static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CHECK_TIMEOUT = Duration.ofMinutes(2);
    /** 복원 첫 단계: 지금 표를 모두 지운다(같은 트랜잭션). 다른 연결이 표를 붙잡고 있으면 오래 기다리지 않고 실패한다. */
    static final String RESET_SQL = """
            SET lock_timeout = '60s';
            DROP SCHEMA public CASCADE;
            CREATE SCHEMA public;
            """;

    /** 복원할 파일 위치: 백업 폴더 / import 폴더(서버 관리자가 넣은 외부 파일). */
    public enum Source {
        BACKUP, IMPORT;

        public static Source of(String value) {
            if (value == null || value.isBlank() || "backup".equalsIgnoreCase(value)) {
                return BACKUP;
            }
            if ("import".equalsIgnoreCase(value)) {
                return IMPORT;
            }
            throw new BusinessException(ErrorCode.BACKUP_INVALID_NAME);
        }
    }

    /**
     * 복원 전 확인 결과(확인 창에 보여 줌).
     *
     * @param dbVersion        백업의 DB 버전
     * @param currentVersion   지금 앱 DB 버전
     * @param checksumVerified 정보 파일의 체크섬과 맞춰 봤는지(정보 파일이 없으면 false)
     */
    public record Check(String fileName, Source source, Kind kind, LocalDateTime createdAt, long size,
                        String dbVersion, String currentVersion, boolean checksumVerified) {
    }

    /** @param preRestoreFile 복원 직전 상태의 백업(되돌릴 때 씀) */
    public record Result(String fileName, String preRestoreFile, String fromVersion, String toVersion,
                         int migrationsApplied, int sessionsCleared) {
    }

    /** 복원이 끝났음(자동 백업 설정처럼 DB 에서 읽어 둔 것을 다시 읽게 한다). */
    public record Restored(String fileName) {
    }

    private final BackupService backups;
    private final MaintenanceMode maintenance;
    private final DbVersionSource dbVersion;
    private final SchemaMigrator migrator;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final AuditService audit;
    private final BackupMessenger messenger;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean restoring = new AtomicBoolean();

    public RestoreService(BackupService backups, MaintenanceMode maintenance, DbVersionSource dbVersion,
                          SchemaMigrator migrator, JdbcTemplate jdbc, DataSource dataSource, AuditService audit,
                          BackupMessenger messenger, ApplicationEventPublisher events) {
        this.backups = backups;
        this.maintenance = maintenance;
        this.dbVersion = dbVersion;
        this.migrator = migrator;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.audit = audit;
        this.messenger = messenger;
        this.events = events;
    }

    /** 복원 전 확인만 한다(확인 창). 문제가 있으면 그 이유로 예외. */
    public Check check(String fileName, Source source) {
        return verify(resolve(fileName, source), source);
    }

    /**
     * 복원. 끝나면 모든 세션이 지워지므로 화면은 로그인 화면으로 보낸다.
     *
     * @throws BusinessException 확인 실패(체크섬·파일·버전), 복원 전 백업 실패, 복원 실패(DB 는 그대로)
     */
    public Result restore(String fileName, Source source, Long actorId, String actorName) {
        if (!restoring.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.BACKUP_IN_PROGRESS, "복원이 진행 중입니다.");
        }
        try {
            return doRestore(fileName, source, actorId, actorName);
        } finally {
            restoring.set(false);
        }
    }

    private Result doRestore(String fileName, Source source, Long actorId, String actorName) {
        Check check;
        BackupFile pre;
        try {
            check = verify(resolve(fileName, source), source);
            pre = preRestoreBackup();
        } catch (BusinessException ex) {
            recordAudit(actorId, actorName, false, describe(fileName, source, null, null) + " | " + ex.getMessage());
            throw ex;
        }

        if (!maintenance.begin()) {
            throw new BusinessException(ErrorCode.BACKUP_IN_PROGRESS, "복원이 진행 중입니다.");
        }
        RuntimeException failure = null;
        int migrations = 0;
        int sessions = 0;
        try {
            drain();
            apply(openForRestore(resolve(fileName, source)));
            migrations = migrator.migrate();
            evictConnections();
            sessions = clearSessions();
        } catch (RuntimeException ex) {
            failure = ex;
        } finally {
            maintenance.end();
        }

        String detail = describe(fileName, source, check, pre.fileName());
        if (failure != null) {
            log.error("[복원] 실패: {} (복원 전 백업 {})", fileName, pre.fileName(), failure);
            recordAudit(actorId, actorName, false, detail + " | " + brief(failure));
            throw new BusinessException(ErrorCode.BACKUP_RESTORE_FAILED,
                    "복원에 실패했습니다. 복원 전 백업(" + pre.fileName() + ")으로 되돌릴 수 있습니다.");
        }
        String toVersion = dbVersion.current();
        log.info("[복원] 완료: {} (DB 버전 {} → {}, 마이그레이션 {}개, 세션 {}개 삭제, 복원 전 백업 {})",
                fileName, check.dbVersion(), toVersion, migrations, sessions, pre.fileName());
        // 감사 로그 표도 복원으로 되돌아갔으므로 복원 기록은 지금 새로 쓴다
        recordAudit(actorId, actorName, true, detail);
        events.publishEvent(new Restored(fileName));
        try {
            messenger.restored(actorName, fileName, check.createdAt(), pre.fileName());
        } catch (RuntimeException ex) {
            log.warn("[복원] 완료 알림 실패: {}", ex.getMessage());
        }
        return new Result(fileName, pre.fileName(), check.dbVersion(), toVersion, migrations, sessions);
    }

    private Path resolve(String fileName, Source source) {
        return source == Source.IMPORT ? backups.resolveImport(fileName) : backups.resolve(fileName);
    }

    /**
     * 복원할 파일을 pg_restore 가 읽을 수 있는 형태로 연다. 복원에서 파일을 읽는 곳은 여기 하나다.
     * 지금은 그대로 돌려주고, 다음 단계(암호화)에서 복호화한 임시 파일을 돌려주게 바꾼다.
     */
    Path openForRestore(Path file) {
        return file;
    }

    /** 체크섬(정보 파일이 있으면) → 정상 백업 파일인지(pg_restore --list) → DB 버전. */
    private Check verify(Path file, Source source) {
        BackupFile meta = backups.describe(file, source == Source.IMPORT)
                .orElseThrow(() -> new BusinessException(ErrorCode.BACKUP_NOT_FOUND));
        Optional<BackupInfo> info = backups.readInfo(file);
        boolean checksumVerified = false;
        if (info.isPresent() && StringUtils.hasText(info.get().sha256())) {
            String actual;
            try {
                actual = BackupService.sha256(file);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.BACKUP_INVALID_FILE);
            }
            if (!info.get().sha256().equalsIgnoreCase(actual)) {
                throw new BusinessException(ErrorCode.BACKUP_CHECKSUM_MISMATCH);
            }
            checksumVerified = true;
        }
        Path readable = openForRestore(file);
        BackupProcessRunner.Result list = backups.runTool(List.of(backups.tool("pg_restore"), "--list",
                readable.toString()), Map.of(), CHECK_TIMEOUT);
        if (list.exitCode() != 0) {
            log.warn("[복원] 올바른 백업 파일이 아님: {} ({})", file.getFileName(), list.output());
            throw new BusinessException(ErrorCode.BACKUP_INVALID_FILE);
        }
        String version = info.map(BackupInfo::dbVersion).filter(StringUtils::hasText)
                .orElseGet(() -> backups.dbVersionInDump(readable));
        if (version == null) {
            throw new BusinessException(ErrorCode.BACKUP_INVALID_FILE,
                    "연차관리 앱의 백업이 아닙니다(백업 안에 DB 버전 기록이 없습니다).");
        }
        String current = dbVersion.current();
        if (current != null && DbVersionSource.compare(version, current) > 0) {
            throw new BusinessException(ErrorCode.BACKUP_NEWER_VERSION,
                    "이 백업은 더 새 버전의 앱에서 만들어졌습니다(백업 DB 버전 " + version + ", 지금 " + current
                            + "). 앱을 먼저 업데이트하세요.");
        }
        return new Check(file.getFileName().toString(), source, meta.kind(), meta.createdAt(), meta.size(), version,
                current, checksumVerified);
    }

    private BackupFile preRestoreBackup() {
        try {
            return backups.backup(Kind.PRE_RESTORE);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.BACKUP_RESTORE_FAILED,
                    "복원 전 백업에 실패해 복원하지 않았습니다. " + ex.getMessage());
        }
    }

    /** 처리 중인 다른 요청이 끝나기를 잠깐 기다린다(복원 요청 자신 1개 제외). 넘기면 그대로 진행한다. */
    private void drain() {
        try {
            if (!maintenance.awaitIdle(1, DRAIN_TIMEOUT)) {
                log.warn("[복원] 처리 중인 요청이 {}초 안에 끝나지 않아 그대로 진행합니다.", DRAIN_TIMEOUT.toSeconds());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("복원이 중단되었습니다.", e);
        }
    }

    /** 한 트랜잭션으로 "모든 표 지우기 → 백업 내용 넣기". 실패하면 psql 이 전부 되돌린다. */
    private void apply(Path readable) {
        Path work = backups.workDir();
        String stamp = String.valueOf(System.currentTimeMillis());
        Path reset = work.resolve(".restore-" + stamp + "-reset.sql.tmp");
        Path sql = work.resolve(".restore-" + stamp + "-data.sql.tmp");
        try {
            Files.writeString(reset, RESET_SQL);
            BackupProcessRunner.Result toSql = backups.runTool(List.of(backups.tool("pg_restore"), "--no-owner",
                    "-f", sql.toString(), readable.toString()), Map.of(), RESTORE_TIMEOUT);
            if (toSql.exitCode() != 0) {
                throw new IllegalStateException("pg_restore 종료 코드 " + toSql.exitCode() + ": " + toSql.output());
            }
            List<String> psql = new ArrayList<>(List.of(backups.tool("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1",
                    "--single-transaction"));
            psql.addAll(backups.connectionArgs());
            psql.addAll(List.of("-f", reset.toString(), "-f", sql.toString()));
            BackupProcessRunner.Result applied = backups.runTool(psql, backups.passwordEnv(), RESTORE_TIMEOUT);
            if (applied.exitCode() != 0) {
                throw new IllegalStateException("psql 종료 코드 " + applied.exitCode() + ": " + applied.output());
            }
        } catch (IOException e) {
            throw new IllegalStateException("복원 임시 파일을 만들 수 없습니다: " + e.getMessage(), e);
        } finally {
            deleteQuietly(reset);
            deleteQuietly(sql);
        }
    }

    /** 표를 새로 만들었으니 연결 풀의 기존 연결(준비해 둔 쿼리)을 버리고 새로 받게 한다. */
    private void evictConnections() {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                pool.softEvictConnections();
            }
        }
    }

    /** 모든 로그인 세션 삭제(복원한 관리자 본인 포함). 복원된 직원·권한 기준으로 다시 로그인하게 한다. */
    private int clearSessions() {
        return jdbc.update("DELETE FROM spring_session");
    }

    private void recordAudit(Long actorId, String actorName, boolean success, String detail) {
        try {
            audit.record(actorId, actorName, "restore", "backups", null,
                    detail.length() > 1000 ? detail.substring(0, 1000) : detail, success);
        } catch (RuntimeException ex) {
            log.warn("[복원] 감사 로그 기록 실패: {}", ex.getMessage());
        }
    }

    private static String describe(String fileName, Source source, Check check, String preRestoreFile) {
        StringBuilder sb = new StringBuilder("파일: ").append(fileName);
        if (source == Source.IMPORT) {
            sb.append("(가져온 파일)");
        }
        if (check != null) {
            sb.append(" · 백업 시점 ").append(check.createdAt())
                    .append(" · DB 버전 ").append(check.dbVersion()).append(" → ").append(check.currentVersion());
        }
        if (preRestoreFile != null) {
            sb.append(" · 복원 전 백업: ").append(preRestoreFile);
        }
        return sb.toString();
    }

    /** 이벤트 로그에는 오류 첫 줄만(자세한 내용은 서버 로그). */
    private static String brief(RuntimeException ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage().strip();
        String first = m.lines().findFirst().orElse(m);
        return first.length() > 200 ? first.substring(0, 200) : first;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[복원] 임시 파일 삭제 실패: {}", path);
        }
    }
}
