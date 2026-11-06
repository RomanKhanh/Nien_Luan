package com.brainblocks.backend.config;

import com.brainblocks.backend.enums.NotificationType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTypeMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", ""})
    void extendsLegacyCheckAndPreservesRows(String mode) {
        var ds = new SingleConnectionDataSource("jdbc:h2:mem:notification-migration-" + System.nanoTime() + mode,
                "sa", "", true);
        try {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE notifications (type VARCHAR(40) NOT NULL, "
                    + "CONSTRAINT notifications_type_check CHECK (type IN ('NEW_ORDER')))");
            jdbc.update("INSERT INTO notifications VALUES ('NEW_ORDER')");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO notifications VALUES ('LOW_STOCK')"))
                    .isInstanceOf(DataIntegrityViolationException.class);

            var migration = new NotificationTypeMigration(jdbc);
            migration.run();
            migration.run();
            for (var type : NotificationType.values()) {
                jdbc.update("INSERT INTO notifications VALUES (?)", type.name());
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                    .isEqualTo(NotificationType.values().length + 1);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO notifications VALUES ('INVALID')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            ds.destroy();
        }
    }
}
