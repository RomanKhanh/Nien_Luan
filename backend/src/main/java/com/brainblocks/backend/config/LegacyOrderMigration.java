package com.brainblocks.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Chuyển dữ liệu đơn hàng cũ lúc khởi động (ddl-auto=update chỉ thêm cột, không sửa ràng buộc hay dữ liệu):
 * <ol>
 *   <li>Đơn trước khi có địa chỉ 3 cấp lưu địa chỉ ở cột shipping_address (NOT NULL); đơn mới để trống cột này
 *       nên gỡ NOT NULL.</li>
 *   <li>Đơn trước khi có phí vận chuyển: subtotal = total_amount, shipping_fee = 0 (miền và số kiện để null).</li>
 *   <li>Đơn khách tự hủy trước khi có cột cancelled_by_customer: đánh dấu lại theo thông báo hủy đã gửi admin.</li>
 * </ol>
 * Chạy lại nhiều lần không sao: chỉ đụng tới ràng buộc / dòng còn ở dạng cũ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyOrderMigration implements CommandLineRunner {
    static final String TABLE = "orders";
    static final String LEGACY_ADDRESS_COLUMN = "shipping_address";
    // tiêu đề thông báo admin khi khách tự hủy, phải khớp OrderService.cancelOrder
    public static final String SELF_CANCEL_TITLE = "Khách đã hủy đơn ";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (isNotNull()) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ALTER COLUMN " + LEGACY_ADDRESS_COLUMN + " DROP NOT NULL");
            log.info("Dropped NOT NULL on {}.{} (legacy free-text address)", TABLE, LEGACY_ADDRESS_COLUMN);
        }
        int filled = jdbcTemplate.update("UPDATE " + TABLE + " SET subtotal = total_amount WHERE subtotal IS NULL");
        jdbcTemplate.update("UPDATE " + TABLE + " SET shipping_fee = 0 WHERE shipping_fee IS NULL");
        if (filled > 0) {
            log.info("Filled subtotal / shipping fee for {} orders placed before shipping fees", filled);
        }
        // Đơn khách tự hủy trước khi có cột cancelled_by_customer: nhận ra qua thông báo "Khách đã hủy đơn <mã>"
        // mà OrderService.cancelOrder gửi admin. Đơn admin hủy không có thông báo này nên giữ null.
        int selfCancelled = jdbcTemplate.update("UPDATE " + TABLE + " SET cancelled_by_customer = TRUE"
                + " WHERE status = 'CANCELLED' AND cancelled_by_customer IS NULL AND EXISTS ("
                + "SELECT 1 FROM notifications n WHERE n.type = 'ORDER_CANCELLED_BY_CUSTOMER'"
                + " AND n.title = CONCAT('" + SELF_CANCEL_TITLE + "', " + TABLE + ".order_code))");
        if (selfCancelled > 0) {
            log.info("Marked {} orders cancelled by the customer before the cancelled_by_customer column", selfCancelled);
        }
    }

    boolean isNotNull() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) -> {
            DatabaseMetaData meta = connection.getMetaData();
            // tên bảng / cột trong metadata có thể là chữ thường (PostgreSQL) hoặc chữ hoa (H2 mặc định)
            for (String table : new String[]{TABLE, TABLE.toUpperCase()}) {
                for (String column : new String[]{LEGACY_ADDRESS_COLUMN, LEGACY_ADDRESS_COLUMN.toUpperCase()}) {
                    Boolean notNull = readNotNull(meta, connection.getSchema(), table, column);
                    if (notNull != null) {
                        return notNull;
                    }
                }
            }
            return false;
        }));
    }

    private static Boolean readNotNull(DatabaseMetaData meta, String schema, String table, String column)
            throws SQLException {
        try (ResultSet rs = meta.getColumns(null, schema, table, column)) {
            return rs.next() ? Objects.equals(rs.getString("IS_NULLABLE"), "NO") : null;
        }
    }
}
