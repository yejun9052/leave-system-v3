package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupDtos.Overview;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * DB 백업(pg_dump). 백업 폴더(app.backup.dir) 아래에만 쓰고, 한 번에 하나만 실행한다.
 * <ul>
 *   <li>파일 이름: annual_leave_yyyyMMdd_HHmmss_종류.dump (pg_dump -Fc 형식)</li>
 *   <li>.tmp 이름으로 쓰고 성공하면 이름을 바꾼다. 실패하면 만들다 만 파일을 지운다(목록에 미완성 파일이 보이지 않게)</li>
 *   <li>세션 테이블은 구조만 남긴다(복원했을 때 예전 로그인이 살아나지 않게)</li>
 *   <li>DB 비밀번호는 명령 인자가 아닌 환경변수 PGPASSWORD 로 넘긴다(프로세스 목록 노출 방지)</li>
 *   <li>백업마다 같은 이름의 .json(만든 시각·종류·DB 버전·크기·SHA-256)을 함께 만든다({@link BackupInfo})</li>
 * </ul>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    static final Pattern FILE_NAME = Pattern.compile("annual_leave_(\\d{8}_\\d{6})_(manual|auto|pre-restore)\\.dump");
    /** import/ 폴더 파일 이름: 영문·숫자·._- 만, .dump 로 끝나고 100자 이하. */
    static final Pattern IMPORT_NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,94}\\.dump");
    static final Duration TIMEOUT = Duration.ofMinutes(10);
    /** 서버 관리자가 외부 백업 파일을 직접 넣는 하위 폴더. 목록에 "가져온 파일"로 보이고 복원할 수 있다. */
    static final String IMPORT_DIR = "import";
    private static final List<String> SESSION_TABLES = List.of("spring_session", "spring_session_attributes");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Path dir;
    private final Path importDir;
    private final BackupProperties props;
    private final String pgDump;
    private final DbVersionSource dbVersion;
    private final ObjectMapper json;
    private final DbTarget db;
    private final BackupProcessRunner runner;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    /** pg_dump 접속 대상. JDBC 주소(jdbc:postgresql://host:port/db)에서 꺼낸다. */
    record DbTarget(String host, int port, String database, String username, String password) {

        static DbTarget fromJdbcUrl(String jdbcUrl, String username, String password) {
            String prefix = "jdbc:postgresql://";
            if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("지원하지 않는 DB 주소: " + jdbcUrl);
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (uri.getHost() == null || path == null || path.length() < 2) {
                throw new IllegalArgumentException("DB 주소에서 호스트·DB 이름을 읽을 수 없음: " + jdbcUrl);
            }
            return new DbTarget(uri.getHost(), uri.getPort() < 0 ? 5432 : uri.getPort(), path.substring(1),
                    username, password);
        }
    }

    @Autowired
    public BackupService(BackupProperties props, BackupProcessRunner runner, DbVersionSource dbVersion,
                         ObjectMapper json,
                         @Value("${spring.datasource.url:}") String jdbcUrl,
                         @Value("${spring.datasource.username:}") String username,
                         @Value("${spring.datasource.password:}") String password) {
        this(props, runner, parse(jdbcUrl, username, password), Clock.system(SEOUL), dbVersion, json);
    }

    BackupService(BackupProperties props, BackupProcessRunner runner, DbTarget db, Clock clock,
                  DbVersionSource dbVersion, ObjectMapper json) {
        if (props == null || !StringUtils.hasText(props.dir())) {
            throw new IllegalStateException("app.backup.dir 설정이 필요합니다.");
        }
        this.dir = Path.of(props.dir()).toAbsolutePath().normalize();
        this.importDir = dir.resolve(IMPORT_DIR);
        this.props = props;
        this.pgDump = props.pgDump();
        this.dbVersion = dbVersion;
        this.json = json;
        this.db = db;
        this.runner = runner;
        this.clock = clock;
    }

    private static DbTarget parse(String jdbcUrl, String username, String password) {
        try {
            return DbTarget.fromJdbcUrl(jdbcUrl, username, password);
        } catch (IllegalArgumentException e) {
            // 앱 기동은 막지 않고, 백업할 때 실패로 알린다
            log.warn("백업 비활성: {}", e.getMessage());
            return null;
        }
    }

    /** 지금 백업. 끝날 때까지 기다린 뒤 만든 파일을 돌려준다. 진행 중이면 거부. */
    public BackupFile backup(Kind kind) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.BACKUP_IN_PROGRESS);
        }
        try {
            return dump(kind);
        } finally {
            running.set(false);
        }
    }

    private BackupFile dump(Kind kind) {
        if (db == null) {
            throw failed("DB 접속 주소(spring.datasource.url)를 읽을 수 없습니다.");
        }
        prepareDirs();
        LocalDateTime createdAt = LocalDateTime.now(clock).withNano(0);
        // 이름은 초 단위라 같은 초에 같은 종류를 또 만들면(예: 복원 직후 다시 복원) 다음 초까지 기다렸다 만든다
        for (int retry = 0; Files.exists(dir.resolve(fileName(createdAt, kind))) && retry < 2; retry++) {
            sleepQuietly(Duration.ofMillis(1000 - LocalDateTime.now(clock).getNano() / 1_000_000 + 10));
            createdAt = LocalDateTime.now(clock).withNano(0);
        }
        String name = fileName(createdAt, kind);
        Path target = dir.resolve(name);
        if (Files.exists(target)) {
            throw failed("같은 시각의 백업 파일이 이미 있습니다. 잠시 후 다시 시도하세요.");
        }
        Path tmp = dir.resolve(name + ".tmp");
        try {
            BackupProcessRunner.Result result = runPgDump(tmp);
            if (result.exitCode() != 0) {
                log.warn("pg_dump 실패(종료 코드 {}): {}", result.exitCode(), result.output());
                throw failed("pg_dump 종료 코드 " + result.exitCode() + lastLine(result.output()));
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw failed("백업 파일을 저장하지 못했습니다(" + e.getMessage() + ")");
        } finally {
            deleteQuietly(tmp);
        }
        writeInfo(target, kind, createdAt);
        BackupFile file = toBackupFile(target)
                .orElseThrow(() -> failed("만든 파일을 읽을 수 없습니다: " + target));
        log.info("백업 완료: {} ({} bytes)", file.fileName(), file.size());
        return file;
    }

    private BackupProcessRunner.Result runPgDump(Path tmp) {
        List<String> command = new ArrayList<>(List.of(pgDump, "-Fc", "-w"));
        SESSION_TABLES.forEach(t -> command.add("--exclude-table-data=" + t));
        command.addAll(List.of("-h", db.host(), "-p", String.valueOf(db.port()), "-U", db.username(),
                "-f", tmp.toString(), db.database()));
        Map<String, String> env = StringUtils.hasLength(db.password()) ? Map.of("PGPASSWORD", db.password()) : Map.of();
        try {
            return runner.run(command, env, TIMEOUT);
        } catch (IOException e) {
            throw failed("pg_dump 를 실행할 수 없습니다. 설치 경로(app.backup.pg-bin)를 확인하세요. " + e.getMessage());
        } catch (TimeoutException e) {
            throw failed("제한 시간(" + TIMEOUT.toMinutes() + "분)을 넘겨 중단했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("백업이 중단되었습니다.");
        }
    }

    /** 백업 탭: 폴더·남은 공간·진행 여부와 백업 목록(최신순). */
    public Overview overview() {
        prepareDirs();
        return new Overview(dir.toString(), dir.toFile().getUsableSpace(), running.get(), listBackups(), listImports(),
                dbVersion.current());
    }

    /** 백업 폴더의 백업 파일(최신순). */
    private List<BackupFile> listBackups() {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(this::toBackupFile)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(BackupFile::createdAt).thenComparing(BackupFile::fileName).reversed())
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "백업 폴더를 읽을 수 없습니다: " + dir);
        }
    }

    /** import/ 폴더의 가져온 파일(최신순). 만든 시각은 파일 수정 시각. */
    private List<BackupFile> listImports() {
        try (Stream<Path> paths = Files.list(importDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> IMPORT_NAME.matcher(p.getFileName().toString()).matches())
                    .map(this::toImportFile)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(BackupFile::createdAt).thenComparing(BackupFile::fileName).reversed())
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "가져온 파일 폴더를 읽을 수 없습니다: " + importDir);
        }
    }

    /**
     * 자동 백업 보관 정리({@link BackupRetention}): 최근 keepMonths 개월보다 오래된 자동 백업 삭제.
     * 새 자동 백업이 성공한 뒤에만 부른다.
     * 수동·복원 전 백업은 지우지 않는다. 지우지 못한 파일은 건너뛰고 로그만 남긴다.
     *
     * @return 지운 파일 이름(오래된 것부터)
     */
    public List<String> cleanupAuto(int keepMonths) {
        List<String> deleted = new ArrayList<>();
        for (BackupFile old : BackupRetention.toDelete(listBackups(), keepMonths, LocalDateTime.now(clock))) {
            try {
                Files.deleteIfExists(dir.resolve(old.fileName()));
                Files.deleteIfExists(infoPath(dir.resolve(old.fileName())));
                deleted.add(old.fileName());
            } catch (IOException e) {
                log.warn("오래된 자동 백업 삭제 실패: {} ({})", old.fileName(), e.getMessage());
            }
        }
        if (!deleted.isEmpty()) {
            log.info("자동 백업 보관 정리: {}개 삭제 {}", deleted.size(), deleted);
        }
        return deleted;
    }

    /** 백업 파일 삭제(목록의 삭제 버튼). 진행 중인 백업 파일(.tmp)은 이름 형식이 달라 지울 수 없다. */
    public void delete(String fileName) {
        Path file = resolve(fileName);
        try {
            Files.delete(file);
            Files.deleteIfExists(infoPath(file));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "백업 파일을 지우지 못했습니다: " + fileName);
        }
        log.info("백업 삭제: {}", fileName);
    }

    /**
     * 내려받을 파일. 정해진 이름 형식만 허용하고 백업 폴더 바로 아래 파일만 돌려준다(경로 조작 방지).
     */
    public Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ErrorCode.BACKUP_INVALID_NAME);
        }
        Path file = dir.resolve(fileName).normalize();
        if (!dir.equals(file.getParent()) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.BACKUP_NOT_FOUND);
        }
        return file;
    }

    /** import/ 폴더의 가져온 파일. 이름 규칙을 지키고 import/ 바로 아래에 있어야 한다(경로 조작 방지). */
    public Path resolveImport(String fileName) {
        if (fileName == null || !IMPORT_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ErrorCode.BACKUP_INVALID_NAME);
        }
        Path file = importDir.resolve(fileName).normalize();
        if (!importDir.equals(file.getParent()) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.BACKUP_NOT_FOUND);
        }
        return file;
    }

    /** 백업 정보 파일 경로: 같은 이름의 .json. */
    static Path infoPath(Path dump) {
        return dump.resolveSibling(dump.getFileName().toString().replaceFirst("\\.dump$", ".json"));
    }

    /** 백업 정보 파일. 없거나 읽을 수 없으면 비어 있다. */
    Optional<BackupInfo> readInfo(Path dump) {
        Path info = infoPath(dump);
        if (!Files.isRegularFile(info)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(Files.readString(info), BackupInfo.class));
        } catch (IOException | JacksonException e) {
            log.warn("백업 정보 파일을 읽을 수 없음: {} ({})", info, e.getMessage());
            return Optional.empty();
        }
    }

    /** 백업 정보 파일 작성. 실패해도 백업은 성공으로 둔다(복원할 때 백업 안에서 버전을 확인한다). */
    private void writeInfo(Path dump, Kind kind, LocalDateTime createdAt) {
        Path info = infoPath(dump);
        Path tmp = info.resolveSibling(info.getFileName() + ".tmp");
        try {
            BackupInfo content = new BackupInfo(dump.getFileName().toString(), createdAt.toString(), kind.code(),
                    dbVersion.current(), Files.size(dump), sha256(dump));
            Files.writeString(tmp, json.writerWithDefaultPrettyPrinter().writeValueAsString(content));
            Files.move(tmp, info, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.warn("백업 정보 파일을 만들지 못했습니다: {} ({})", info, e.getMessage());
            deleteQuietly(tmp);
        }
    }

    /** 파일 SHA-256(16진수 소문자). */
    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 정보 파일이 없는 백업(1단계 백업, 가져온 파일)의 DB 버전: 백업 안의 flyway_schema_history 를 꺼내 읽는다.
     *
     * @return 성공한 마이그레이션 중 가장 높은 버전. 기록이 없으면 null
     * @throws BusinessException 백업 파일을 읽을 수 없을 때(BACKUP_INVALID_FILE)
     */
    String dbVersionInDump(Path dump) {
        BackupProcessRunner.Result result = runTool(List.of(props.tool("pg_restore"), "--data-only",
                "--table=flyway_schema_history", "-f", "-", dump.toString()), Map.of(), Duration.ofMinutes(2));
        if (result.exitCode() != 0) {
            log.warn("백업 안의 DB 버전 확인 실패(종료 코드 {}): {}", result.exitCode(), result.output());
            throw new BusinessException(ErrorCode.BACKUP_INVALID_FILE);
        }
        return flywayVersionIn(result.output());
    }

    /** pg_restore 가 꺼낸 COPY 블록에서 성공한(success = t) 마이그레이션의 가장 높은 버전. */
    static String flywayVersionIn(String sql) {
        String[] lines = sql.split("\\R");
        String best = null;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].startsWith("COPY ") || !lines[i].contains("flyway_schema_history")) {
                continue;
            }
            List<String> columns = Arrays.stream(lines[i].substring(lines[i].indexOf('(') + 1, lines[i].indexOf(')'))
                    .split(",")).map(String::strip).toList();
            int version = columns.indexOf("version");
            int success = columns.indexOf("success");
            for (int j = i + 1; j < lines.length && !lines[j].equals("\\."); j++) {
                String[] row = lines[j].split("\t", -1);
                if (row.length <= Math.max(version, success) || !"t".equals(row[success])
                        || "\\N".equals(row[version])) {
                    continue;
                }
                if (best == null || DbVersionSource.compare(row[version], best) > 0) {
                    best = row[version];
                }
            }
        }
        return best;
    }

    /** PostgreSQL 클라이언트 도구 실행(시작 실패·시간 초과는 BACKUP_FAILED). */
    BackupProcessRunner.Result runTool(List<String> command, Map<String, String> env, Duration timeout) {
        try {
            return runner.run(command, env, timeout);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED,
                    command.get(0) + " 를 실행할 수 없습니다. 설치 경로(app.backup.pg-bin)를 확인하세요. " + e.getMessage());
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "제한 시간(" + timeout.toMinutes() + "분)을 넘겨 중단했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "작업이 중단되었습니다.");
        }
    }

    /** 목록과 같은 정보(종류·만든 시각·DB 버전). 가져온 파일이면 imported. */
    Optional<BackupFile> describe(Path file, boolean imported) {
        return imported ? toImportFile(file) : toBackupFile(file);
    }

    /** PostgreSQL 클라이언트 도구 경로(pg_restore·psql). */
    String tool(String name) {
        return props.tool(name);
    }

    /** psql 접속 인자(-h -p -U -d). 비밀번호는 {@link #passwordEnv()} 로 따로 넘긴다. */
    List<String> connectionArgs() {
        if (db == null) {
            throw failed("DB 접속 주소(spring.datasource.url)를 읽을 수 없습니다.");
        }
        return List.of("-h", db.host(), "-p", String.valueOf(db.port()), "-U", db.username(), "-d", db.database());
    }

    /** DB 비밀번호 환경변수(PGPASSWORD). 명령 인자로 넘기지 않는다(프로세스 목록 노출 방지). */
    Map<String, String> passwordEnv() {
        return db != null && StringUtils.hasLength(db.password()) ? Map.of("PGPASSWORD", db.password()) : Map.of();
    }

    /** 복원 중 임시 파일을 둘 폴더(백업 폴더). 임시 파일 이름은 목록 규칙과 겹치지 않게 .tmp 로 끝낸다. */
    Path workDir() {
        prepareDirs();
        return dir;
    }

    private void prepareDirs() {
        try {
            Files.createDirectories(dir.resolve(IMPORT_DIR));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BACKUP_FAILED, "백업 폴더를 만들 수 없습니다(권한 확인): " + dir);
        }
    }

    private Optional<BackupFile> toBackupFile(Path path) {
        Matcher m = FILE_NAME.matcher(path.getFileName().toString());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BackupFile(path.getFileName().toString(), LocalDateTime.parse(m.group(1), STAMP),
                    Files.size(path), Kind.ofCode(m.group(2)), readInfo(path).map(BackupInfo::dbVersion).orElse(null)));
        } catch (DateTimeParseException | IOException e) {
            return Optional.empty();
        }
    }

    private Optional<BackupFile> toImportFile(Path path) {
        try {
            LocalDateTime modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), SEOUL)
                    .withNano(0);
            return Optional.of(new BackupFile(path.getFileName().toString(), modified, Files.size(path), Kind.IMPORTED,
                    readInfo(path).map(BackupInfo::dbVersion).orElse(null)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String lastLine(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String[] lines = output.strip().split("\\R");
        String last = lines[lines.length - 1].strip();
        return ": " + (last.length() > 200 ? last.substring(0, 200) : last);
    }

    private static String fileName(LocalDateTime createdAt, Kind kind) {
        return "annual_leave_" + createdAt.format(STAMP) + "_" + kind.code() + ".dump";
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(Math.max(0, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("임시 백업 파일 삭제 실패: {}", path);
        }
    }

    private static BusinessException failed(String detail) {
        return new BusinessException(ErrorCode.BACKUP_FAILED, "백업에 실패했습니다. " + detail);
    }
}
