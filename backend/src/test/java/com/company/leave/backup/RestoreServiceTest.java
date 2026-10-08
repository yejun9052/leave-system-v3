package com.company.leave.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.RestoreService.Source;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 복원 순서와 실패 처리(pg 도구는 가짜). 실제 DB 로 복원하는 확인은 {@link RestoreIntegrationTest}.
 */
@DisplayName("복원 순서와 실패 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RestoreServiceTest {

    private static final String NAME = "annual_leave_20261001_020000_auto.dump";
    private static final String PRE = "annual_leave_20261008_100000_pre-restore.dump";
    private static final LocalDateTime BACKUP_AT = LocalDateTime.of(2026, 10, 1, 2, 0);

    @TempDir
    Path dir;

    private BackupService backups;
    private final MaintenanceMode maintenance = new MaintenanceMode();
    private SchemaMigrator migrator;
    private JdbcTemplate jdbc;
    private AuditService audit;
    private BackupMessenger messenger;
    private ApplicationEventPublisher events;
    private RestoreService service;
    private Path file;
    /** 실행한 도구와 그때 점검 모드였는지 */
    private final List<String> calls = new ArrayList<>();
    private int psqlExit = 0;

    @BeforeEach
    void setUp() throws Exception {
        file = Files.writeString(dir.resolve(NAME), "PGDMP");
        backups = mock(BackupService.class);
        when(backups.resolve(NAME)).thenReturn(file);
        when(backups.describe(file, false)).thenReturn(Optional.of(new BackupFile(NAME, BACKUP_AT, 5, Kind.AUTO)));
        when(backups.readInfo(file)).thenReturn(Optional.of(new BackupInfo(NAME, BACKUP_AT.toString(), "auto", "8", 5,
                BackupService.sha256(file))));
        when(backups.tool(anyString())).thenAnswer(i -> i.getArgument(0));
        when(backups.workDir()).thenReturn(dir);
        when(backups.connectionArgs()).thenReturn(List.of("-h", "db", "-p", "5432", "-U", "leave", "-d", "annual_leave"));
        when(backups.passwordEnv()).thenReturn(Map.of("PGPASSWORD", "pw"));
        when(backups.backup(Kind.PRE_RESTORE)).thenAnswer(i -> {
            calls.add("pre-restore backup, maintenance=" + maintenance.isActive());
            return new BackupFile(PRE, LocalDateTime.of(2026, 10, 8, 10, 0), 5, Kind.PRE_RESTORE);
        });
        when(backups.runTool(any(), anyMap(), any(Duration.class))).thenAnswer(i -> {
            List<String> cmd = i.getArgument(0);
            calls.add(cmd.get(0) + (cmd.contains("--list") ? " --list" : "") + ", maintenance=" + maintenance.isActive());
            int exit = cmd.get(0).equals("psql") ? psqlExit : 0;
            return new BackupProcessRunner.Result(exit, exit == 0 ? "" : "ERROR:  role \"x\" does not exist\nmore");
        });
        migrator = mock(SchemaMigrator.class);
        when(migrator.migrate()).thenReturn(1);
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("DELETE FROM spring_session")).thenReturn(3);
        audit = mock(AuditService.class);
        messenger = mock(BackupMessenger.class);
        events = mock(ApplicationEventPublisher.class);
        DbVersionSource version = () -> "9";
        service = new RestoreService(backups, maintenance, version, migrator, jdbc, mock(DataSource.class), audit,
                messenger, events);
    }

    @Test
    void 확인_뒤_복원_전_백업을_만들고_점검_모드에서_한_트랜잭션으로_복원한다() {
        RestoreService.Result result = service.restore(NAME, Source.BACKUP, 1L, "관리자");

        assertThat(calls).containsExactly(
                "pg_restore --list, maintenance=false",
                "pre-restore backup, maintenance=false",
                "pg_restore, maintenance=true",
                "psql, maintenance=true");
        assertThat(maintenance.isActive()).isFalse();
        assertThat(result).isEqualTo(new RestoreService.Result(NAME, PRE, "8", "9", 1, 3));
        verify(backups).runTool(argThat(cmd -> cmd.contains("--single-transaction") && cmd.contains("ON_ERROR_STOP=1")
                && !String.join(" ", cmd).contains("pw")), eq(Map.of("PGPASSWORD", "pw")), any(Duration.class));
    }

    @Test
    void 복원한_뒤_마이그레이션_세션_삭제_감사_로그_알림_순서로_처리한다() {
        service.restore(NAME, Source.BACKUP, 1L, "관리자");

        InOrder order = inOrder(migrator, jdbc, audit, events, messenger);
        order.verify(migrator).migrate();
        order.verify(jdbc).update("DELETE FROM spring_session");
        order.verify(audit).record(eq(1L), eq("관리자"), eq("restore"), eq("backups"), eq(null),
                argThat(d -> d.contains(NAME) && d.contains("복원 전 백업: " + PRE) && d.contains("DB 버전 8 → 9")),
                eq(true));
        order.verify(events).publishEvent(new RestoreService.Restored(NAME));
        order.verify(messenger).restored("관리자", NAME, BACKUP_AT, PRE);
    }

    @Test
    void 복원_중간에_실패하면_점검_모드를_풀고_복원_전_백업을_안내하며_실패를_기록한다() {
        psqlExit = 3;

        assertThatThrownBy(() -> service.restore(NAME, Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_RESTORE_FAILED);
                    assertThat(e.getMessage()).isEqualTo(
                            "복원에 실패했습니다. 복원 전 백업(" + PRE + ")으로 되돌릴 수 있습니다.");
                });
        assertThat(maintenance.isActive()).isFalse();
        verify(migrator, never()).migrate();
        verify(messenger, never()).restored(anyString(), anyString(), any(), anyString());
        // 이벤트 로그에는 오류 첫 줄만
        verify(audit).record(eq(1L), eq("관리자"), eq("restore"), eq("backups"), eq(null),
                argThat(d -> d.contains("psql 종료 코드 3") && !d.contains("more")), eq(false));
        assertThat(dir.toFile().list()).containsExactly(NAME); // 임시 SQL 파일 정리
    }

    @Test
    void 체크섬이_다르면_복원_전_백업도_만들지_않고_거부한다() throws Exception {
        Files.writeString(file, "PGDMP-changed");

        assertThatThrownBy(() -> service.restore(NAME, Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_CHECKSUM_MISMATCH));
        assertThat(calls).isEmpty();
        verify(audit).record(eq(1L), eq("관리자"), eq("restore"), eq("backups"), eq(null), anyString(), eq(false));
    }

    @Test
    void 더_새_버전에서_만든_백업은_거부한다() throws Exception {
        when(backups.readInfo(file)).thenReturn(Optional.of(new BackupInfo(NAME, BACKUP_AT.toString(), "auto", "10", 5,
                BackupService.sha256(file))));

        assertThatThrownBy(() -> service.check(NAME, Source.BACKUP))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_NEWER_VERSION);
                    assertThat(e.getMessage()).contains("백업 DB 버전 10, 지금 9");
                });
    }

    @Test
    void 정보_파일이_없으면_백업_안에서_버전을_읽고_체크섬은_확인하지_않은_것으로_보여_준다() {
        when(backups.readInfo(file)).thenReturn(Optional.empty());
        when(backups.dbVersionInDump(file)).thenReturn("7");

        RestoreService.Check check = service.check(NAME, Source.BACKUP);

        assertThat(check.dbVersion()).isEqualTo("7");
        assertThat(check.currentVersion()).isEqualTo("9");
        assertThat(check.checksumVerified()).isFalse();
        assertThat(check.kind()).isEqualTo(Kind.AUTO);
    }

    @Test
    void 깨진_파일은_올바른_백업_파일이_아니다() {
        doReturn(new BackupProcessRunner.Result(1, "pg_restore: error: not a valid archive")).when(backups)
                .runTool(argThat(cmd -> cmd != null && cmd.contains("--list")), anyMap(), any(Duration.class));

        assertThatThrownBy(() -> service.check(NAME, Source.BACKUP))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_INVALID_FILE));
    }

    @Test
    void 복원_전_백업이_실패하면_점검_모드도_켜지_않고_복원하지_않는다() {
        doThrow(new BusinessException(ErrorCode.BACKUP_FAILED, "디스크 부족")).when(backups).backup(Kind.PRE_RESTORE);

        assertThatThrownBy(() -> service.restore(NAME, Source.BACKUP, 1L, "관리자"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BACKUP_RESTORE_FAILED);
                    assertThat(e.getMessage()).contains("복원 전 백업에 실패해 복원하지 않았습니다").contains("디스크 부족");
                });
        assertThat(calls).containsExactly("pg_restore --list, maintenance=false");
        verify(migrator, never()).migrate();
        verify(audit).record(eq(1L), eq("관리자"), eq("restore"), eq("backups"), eq(null), anyString(), anyBoolean());
    }

    @Test
    void 가져온_파일은_import_폴더에서_찾는다() {
        when(backups.resolveImport("old.dump")).thenReturn(file);
        when(backups.describe(file, true)).thenReturn(Optional.of(new BackupFile("old.dump", BACKUP_AT, 5, Kind.IMPORTED)));

        assertThat(service.check("old.dump", Source.IMPORT).kind()).isEqualTo(Kind.IMPORTED);
        assertThat(Source.of("import")).isEqualTo(Source.IMPORT);
        assertThat(Source.of(null)).isEqualTo(Source.BACKUP);
        assertThatThrownBy(() -> Source.of("../etc")).isInstanceOf(BusinessException.class);
    }
}
