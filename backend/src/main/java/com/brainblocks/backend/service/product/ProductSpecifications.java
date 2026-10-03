package com.brainblocks.backend.service.product;

import com.brainblocks.backend.entity.ChildProduct;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.util.SearchTextUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class ProductSpecifications {
    private ProductSpecifications() {
    }

    static Specification<Product> matching(ProductSearchCriteria c, int skillFilterMinImpact, boolean orderByRelevance) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!c.includeInactive()) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            // so trên tên + mô tả đã bỏ dấu, nên gõ "lap rap" vẫn ra "lắp ráp"
            String keyword = SearchTextUtils.normalize(c.keyword());
            if (keyword != null && !keyword.isEmpty()) {
                predicates.add(cb.like(root.get("searchText"), "%" + escapeLike(keyword) + "%", '\\'));
            }
            // "Hợp với bé": bỏ các sản phẩm bé đã có
            if (c.excludeOwnedByChildId() != null) {
                Subquery<Long> owned = query.subquery(Long.class);
                Root<ChildProduct> cp = owned.from(ChildProduct.class);
                owned.select(cp.get("id")).where(
                        cb.equal(cp.get("product"), root),
                        cb.equal(cp.get("childProfile").get("id"), c.excludeOwnedByChildId()));
                predicates.add(cb.not(cb.exists(owned)));
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
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
