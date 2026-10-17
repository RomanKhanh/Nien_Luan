package com.brainblocks.backend.support;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;

/**
 * Chỉ dùng cho test: H2 không có kiểu vector của pgvector và hibernate-vector không hỗ trợ H2,
 * nên cột {@code @JdbcTypeCode(SqlTypes.VECTOR)} được lưu thành mảng REAL. Phép tìm vector ({@code <=>})
 * trong test do InMemoryVectorSearchRepository đảm nhận. Đăng ký qua META-INF/services của src/test.
 */
public class H2VectorTypeContributor implements TypeContributor {
    @Override
    public void contribute(TypeContributions contributions, ServiceRegistry serviceRegistry) {
        var dialect = serviceRegistry.requireService(JdbcServices.class).getDialect();
        if (!(dialect instanceof H2Dialect)) return;
        var types = contributions.getTypeConfiguration();
        var jdbcTypes = types.getJdbcTypeRegistry();
        jdbcTypes.addDescriptor(SqlTypes.VECTOR, new ArrayJdbcType(jdbcTypes.getDescriptor(SqlTypes.FLOAT)) {
            @Override public int getDdlTypeCode() { return SqlTypes.VECTOR; }
        });
        types.getDdlTypeRegistry().addDescriptor(new DdlTypeImpl(SqlTypes.VECTOR, "real array", dialect));
    }
}
