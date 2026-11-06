package com.brainblocks.backend.config;

import com.brainblocks.backend.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Hibernate schema update does not extend an existing enum CHECK constraint. */
@Component
@RequiredArgsConstructor
public class NotificationTypeMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(String... args) {
        var checks = jdbc.queryForList("""
                SELECT cc.check_clause
                FROM information_schema.check_constraints cc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_schema = cc.constraint_schema
                 AND tc.constraint_name = cc.constraint_name
                WHERE LOWER(tc.table_name) = 'notifications'
                  AND LOWER(tc.constraint_name) = 'notifications_type_check'
                  AND tc.table_schema = CURRENT_SCHEMA
                """, String.class);
        if (checks.isEmpty() || checks.stream().allMatch(clause ->
                Arrays.stream(NotificationType.values()).allMatch(type -> clause.contains("'" + type.name() + "'")))) {
            return;
        }
        String allowed = Arrays.stream(NotificationType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT notifications_type_check");
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN ("
                + allowed + "))");
    }
}
