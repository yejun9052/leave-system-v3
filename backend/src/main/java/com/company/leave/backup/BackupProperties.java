package com.company.leave.backup;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 백업 설정 (app.backup.*).
 *
 * @param dir   백업 파일 폴더. 앱은 이 폴더 아래에만 쓴다(운영 컨테이너는 /backups)
 * @param pgBin pg_dump 가 있는 폴더. 비어 있으면 PATH 의 pg_dump
 */
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(String dir, String pgBin) {

    /** 실행할 pg_dump 경로(명령 이름). */
    public String pgDump() {
        return StringUtils.hasText(pgBin) ? Path.of(pgBin, "pg_dump").toString() : "pg_dump";
    }
}
