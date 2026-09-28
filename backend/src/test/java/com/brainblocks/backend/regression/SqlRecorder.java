package com.brainblocks.backend.regression;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

// Ghi lại mọi câu SQL Hibernate gửi đi, để đếm số query theo bảng trong test hồi quy.
// Đăng ký qua property hibernate.session_factory.statement_inspector.
public class SqlRecorder implements StatementInspector {
    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql.toLowerCase(Locale.ROOT));
        return sql;
    }

    public static void clear() {
        STATEMENTS.clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    // số câu SELECT đọc từ bảng tableName (Hibernate viết "from <table> <alias>" hoặc "join <table> <alias>")
    public static long selectsFrom(String tableName) {
        String from = "from " + tableName + " ";
        return STATEMENTS.stream()
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> sql.contains(from))
                .count();
    }
}
