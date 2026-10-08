package com.company.leave.backup;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Component;

/** Flyway 기록(flyway_schema_history)에서 지금 DB 버전을 읽고, 복원한 DB 를 지금 앱 버전으로 올린다. */
@Component
public class FlywayDbVersion implements DbVersionSource, SchemaMigrator {

    private final Flyway flyway;

    public FlywayDbVersion(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public String current() {
        MigrationInfo current = flyway.info().current();
        return current != null && current.getVersion() != null ? current.getVersion().getVersion() : null;
    }

    @Override
    public int migrate() {
        return flyway.migrate().migrationsExecuted;
    }
}
