package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    List<ProductEmbedding> findAllByProductIdIn(Collection<Long> productIds);

    // hash hiện có để biết sản phẩm nào cần embed lại, không tải cả vector 768 chiều
    @Query("select e.product.id as productId, e.contentHash as contentHash from ProductEmbedding e where e.product.id in :ids")
    List<StoredHash> findHashes(@Param("ids") Collection<Long> productIds);

    interface StoredHash {
        Long getProductId();
        String getContentHash();
    }

    // chatbot chỉ embed câu hỏi khi có thứ để so (vector sản phẩm hoặc đoạn tri thức)
    @Query("select count(e) > 0 from ProductEmbedding e where e.product.active = true")
    boolean existsForActiveProducts();
}
