package com.brainblocks.backend.repository;

import java.util.Collection;
import java.util.List;

/**
 * Tìm kiếm theo vector (pgvector). Tách khỏi JPA repository vì dùng SQL riêng của PostgreSQL
 * (toán tử {@code <=>}); test chạy H2 thay bằng bản cài đặt trong bộ nhớ.
 */
public interface VectorSearchRepository {

    // Tối đa limit đoạn của tài liệu INDEXED gần câu hỏi nhất, chỉ giữ đoạn có khoảng cách cosine <= maxDistance;
    // khoảng cách tăng dần, bằng nhau thì id tăng dần.
    List<ChunkMatch> nearestChunks(float[] query, int limit, double maxDistance);

    record ChunkMatch(Long chunkId, Long documentId, String documentTitle, String content, double distance) {}

    // Tối đa limit sản phẩm có vector gần câu hỏi nhất, cùng điều kiện cứng với chatbot: đang bán, còn hàng,
    // hợp tuổi (age null = không lọc tuổi), không thuộc excludedIds; khoảng cách <= maxDistance, tăng dần.
    List<ProductMatch> nearestProducts(float[] query, Integer age, Collection<Long> excludedIds, int limit, double maxDistance);

    record ProductMatch(Long productId, double distance) {}
}
