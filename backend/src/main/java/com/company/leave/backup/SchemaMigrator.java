package com.company.leave.backup;

/** 복원한 DB 를 지금 앱 버전으로 올린다(Flyway migrate). 이미 최신이면 아무것도 하지 않는다. */
public interface SchemaMigrator {

    /** @return 새로 적용한 마이그레이션 수 */
    int migrate();
}
