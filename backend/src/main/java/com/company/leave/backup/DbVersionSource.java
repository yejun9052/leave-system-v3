package com.company.leave.backup;

import org.flywaydb.core.api.MigrationVersion;

/** 앱 DB 버전(Flyway 로 적용된 최신 마이그레이션 버전, 예: "9"). 백업 정보 파일과 복원 전 버전 비교에 쓴다. */
public interface DbVersionSource {

    /** 지금 DB 에 적용된 최신 버전. 알 수 없으면 null. */
    String current();

    /** a 가 b 보다 새 버전이면 양수(Flyway 버전 규칙: "10" > "9", "1.2" > "1.1"). */
    static int compare(String a, String b) {
        return MigrationVersion.fromVersion(a).compareTo(MigrationVersion.fromVersion(b));
    }
}
