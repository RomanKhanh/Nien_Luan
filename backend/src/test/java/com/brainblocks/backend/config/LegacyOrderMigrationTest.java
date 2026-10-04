package com.brainblocks.backend.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chuyển đơn hàng kiểu cũ trên một DB riêng (H2), cả chế độ tên chữ thường (giống PostgreSQL) lẫn chữ hoa:
 * gỡ NOT NULL của shipping_address, điền subtotal = total_amount và shipping_fee = 0 cho đơn trước khi có phí ship.
 */
class LegacyOrderMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = {";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", ""})
    void migratesLegacyOrdersOnceAndKeepsNewOnes(String mode) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:legacy-orders-" + System.nanoTime() + mode, "sa", "", true);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // bảng như sau khi ddl-auto=update thêm cột mới vào DB cũ: cột mới cho phép null
            jdbc.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        shipping_address VARCHAR(255) NOT NULL,
                        total_amount NUMERIC(12, 2) NOT NULL,
                        subtotal NUMERIC(12, 2),
                        shipping_fee NUMERIC(12, 2),
                        order_code VARCHAR(30),
                        status VARCHAR(20),
                        cancelled_by_customer BOOLEAN
                    )""");
            jdbc.execute("CREATE TABLE notifications (id BIGINT PRIMARY KEY, type VARCHAR(40), title VARCHAR(200))");
            jdbc.update("INSERT INTO orders (id, shipping_address, total_amount) VALUES (1, '12 Nguyễn Trãi', 459000)");
            LegacyOrderMigration migration = new LegacyOrderMigration(jdbc);
            assertThat(migration.isNotNull()).isTrue();

            migration.run();
            assertThat(migration.isNotNull()).isFalse();
            Map<String, Object> legacy = jdbc.queryForMap("SELECT subtotal, shipping_fee FROM orders WHERE id = 1");
            assertThat(amount(legacy, "subtotal")).isEqualByComparingTo("459000");
            assertThat(amount(legacy, "shipping_fee")).isEqualByComparingTo("0");

            // đơn mới (có phí ship, không có địa chỉ tự do) không bị đụng tới khi chạy lại
            jdbc.update("INSERT INTO orders (id, shipping_address, total_amount, subtotal, shipping_fee) "
                    + "VALUES (2, NULL, 485000, 459000, 26000)");
            migration.run();
            Map<String, Object> fresh = jdbc.queryForMap("SELECT subtotal, shipping_fee FROM orders WHERE id = 2");
            assertThat(amount(fresh, "subtotal")).isEqualByComparingTo("459000");
            assertThat(amount(fresh, "shipping_fee")).isEqualByComparingTo("26000");

            // đơn hủy cũ: có thông báo "Khách đã hủy đơn <mã>" là khách tự hủy, không có là admin hủy
            jdbc.update("INSERT INTO orders (id, shipping_address, total_amount, order_code, status) "
                    + "VALUES (3, NULL, 100, 'ORD3', 'CANCELLED'), (4, NULL, 100, 'ORD4', 'CANCELLED')");
            jdbc.update("INSERT INTO notifications VALUES (1, 'ORDER_CANCELLED_BY_CUSTOMER', ?)",
                    LegacyOrderMigration.SELF_CANCEL_TITLE + "ORD3");
            jdbc.update("INSERT INTO notifications VALUES (2, 'ORDER_CANCELLED_BY_CUSTOMER', ?)",
                    LegacyOrderMigration.SELF_CANCEL_TITLE + "ORD3");
            migration.run();
            assertThat(jdbc.queryForObject("SELECT cancelled_by_customer FROM orders WHERE id = 3", Boolean.class))
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT cancelled_by_customer FROM orders WHERE id = 4", Boolean.class))
                    .isNull();
        } finally {
            dataSource.destroy();
        }
    }

    // JdbcTemplate trả Map không phân biệt hoa thường tên cột
    private static BigDecimal amount(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }
}
