package com.brainblocks.backend.service.product;

import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ProductSpecifications {
    private ProductSpecifications() {
    }

    static Specification<Product> matching(ProductSearchCriteria c, int skillFilterMinImpact, boolean orderByRelevance) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!c.includeInactive()) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (c.keyword() != null && !c.keyword().isBlank()) {
                String pattern = "%" + escapeLike(c.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (c.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), c.categoryId()));
            }
            // [minAge, maxAge] của sản phẩm giao với [ageFrom, ageTo]
            if (c.ageTo() != null) {
                predicates.add(cb.le(root.get("minAge"), c.ageTo()));
            }
            if (c.ageFrom() != null) {
                predicates.add(cb.ge(root.get("maxAge"), c.ageFrom()));
            }
            if (c.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), c.maxPrice()));
            }
            if (c.inStockOnly()) {
                predicates.add(cb.gt(root.get("stockQuantity"), 0));
            }
            if (c.hasSkillFilter()) {
                Subquery<Long> strongImpact = query.subquery(Long.class);
                Root<ProductSkillImpact> psi = strongImpact.from(ProductSkillImpact.class);
                strongImpact.select(psi.get("id")).where(
                        cb.equal(psi.get("product"), root),
                        psi.get("skill").get("code").in(c.skillCodes()),
                        cb.ge(psi.get("impactIndex"), skillFilterMinImpact));
                predicates.add(cb.exists(strongImpact));
            }
            // chỉ gắn ORDER BY cho câu lấy dữ liệu, không cho câu count của phân trang
            if (orderByRelevance && !isCountQuery(query)) {
                query.orderBy(cb.desc(relevance(c, root, query, cb)), cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // tổng impactIndex của sản phẩm vào các nhóm kỹ năng đang lọc (không lọc = mọi nhóm)
    private static Expression<Integer> relevance(ProductSearchCriteria c, Root<Product> root,
                                                 CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> total = query.subquery(Integer.class);
        Root<ProductSkillImpact> psi = total.from(ProductSkillImpact.class);
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(psi.get("product"), root));
        if (c.hasSkillFilter()) {
            where.add(psi.get("skill").get("code").in(c.skillCodes()));
        }
        total.select(cb.coalesce(cb.sum(psi.get("impactIndex")), 0)).where(where.toArray(Predicate[]::new));
        return total;
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return type == Long.class || type == long.class;
    }

    // từ khóa người dùng gõ có % hoặc _ thì tìm đúng ký tự đó, không coi là wildcard
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
