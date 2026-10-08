package com.company.leave.backup;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 백업 설정 (app.backup.*).
 *
 * @param dir   백업 파일 폴더. 앱은 이 폴더 아래에만 쓴다(운영 컨테이너는 /backups)
 * @param pgBin pg_dump·pg_restore·psql 이 있는 폴더. 비어 있으면 PATH 에서 찾는다
 */
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(String dir, String pgBin) {

    /** 실행할 pg_dump 경로(명령 이름). */
    public String pgDump() {
        return tool("pg_dump");
    }

    /** PostgreSQL 클라이언트 도구(pg_dump·pg_restore·psql) 경로. pgBin 이 비어 있으면 PATH 에서 찾는다. */
    public String tool(String name) {
        return StringUtils.hasText(pgBin) ? Path.of(pgBin, name).toString() : name;
    }
}
