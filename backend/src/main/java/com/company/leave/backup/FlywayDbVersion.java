package com.company.leave.backup;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Component;

/** Flyway 기록(flyway_schema_history)에서 지금 DB 버전을 읽는다. */
@Component
public class FlywayDbVersion implements DbVersionSource {

    private final Flyway flyway;

    public FlywayDbVersion(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public String current() {
        MigrationInfo current = flyway.info().current();
        return current != null && current.getVersion() != null ? current.getVersion().getVersion() : null;
    }
}
