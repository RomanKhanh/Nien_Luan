package com.brainblocks.backend.regression;

import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.service.complaint.AdminComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cho các API mới: danh mục / sản phẩm công khai, quản lý sản phẩm, đánh giá, khiếu nại, thống kê.
 * Gọi HTTP thật (RANDOM_PORT) trên H2, dữ liệu tạo qua API admin nên mỗi test tự chạy độc lập.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CatalogAndFeedbackTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private AdminComplaintService adminComplaintService;

    private String baseUrl;
    private String adminToken;

    record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.path("data");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        adminToken = login("admin@test.local", "test-admin-password");
    }

    // CatalogSeeder luôn bảo đảm 4 nhóm kỹ năng gốc của đề
    @Test
    void baseSkillsAreSeeded() throws Exception {
        List<String> codes = new ArrayList<>();
        call("GET", "/api/skills", null, null).data().forEach(s -> codes.add(s.path("code").asString()));
        assertThat(codes).contains("LOGIC", "CREATIVE", "PROBLEM_SOLVING", "STEM");
    }

    // lọc theo nhóm kỹ năng chỉ lấy sản phẩm tác động >= 5, sắp theo mức độ phù hợp; khoảng tuổi lọc theo giao nhau
    @Test
    void searchFiltersBySkillAndAgeAndSortsByRelevance() throws Exception {
        long categoryId = createCategory();
        long logic = skillId("LOGIC");
        long strong = createProduct(categoryId, "strong", 3, 6, Map.of(logic, 9));
        long medium = createProduct(categoryId, "medium", 3, 6, Map.of(logic, 6));
        long weak = createProduct(categoryId, "weak", 3, 6, Map.of(logic, 4));
        long tooOld = createProduct(categoryId, "old", 10, 14, Map.of(logic, 10));

        Res res = call("GET", "/api/products?categoryId=" + categoryId + "&skills=logic&ageFrom=5&ageTo=8", null, null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(ids(res.data().path("content"))).containsExactly(strong, medium);

        Res all = call("GET", "/api/products?categoryId=" + categoryId + "&sort=price_asc", null, null);
        assertThat(ids(all.data().path("content"))).containsExactlyInAnyOrder(strong, medium, weak, tooOld);

        assertThat(call("GET", "/api/products?sort=unknown", null, null).status()).isEqualTo(400);
        // ký tự % trong từ khóa là ký tự thường, không phải wildcard
        assertThat(call("GET", "/api/products?keyword=%25", null, null).data().path("totalElements").asLong()).isZero();
    }

    // sửa chỉ số kỹ năng -> hồ sơ kỹ năng của bé đang có sản phẩm được tính lại; ẩn sản phẩm -> khách không thấy
    @Test
    void updatingImpactsRecalculatesChildProfilesAndHidingProductHidesIt() throws Exception {
        long categoryId = createCategory();
        long logic = skillId("LOGIC");
        long productId = createProduct(categoryId, "recalc", 3, 12, Map.of(logic, 8));
        String token = newCustomer();
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", productId));
        // alpha mặc định 0.5: một món 8 điểm -> 0.5 x 8
        assertThat(logicScore(token, childId, logic)).isEqualTo(4.0);

        Res updated = call("PUT", "/api/admin/products/" + productId, adminToken, productBody(categoryId, "recalc",
                3, 12, Map.of(logic, 3)));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(logicScore(token, childId, logic)).isEqualTo(1.5);

        assertThat(call("DELETE", "/api/admin/products/" + productId, adminToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/products/" + productId, null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/admin/products/" + productId, adminToken, null).status()).isEqualTo(200);
    }

    // điểm kỹ năng theo lợi ích giảm dần: thêm món điểm thấp không làm tụt điểm,
    // mốc cuối của lộ trình khớp điểm hồ sơ, gỡ món thì điểm về như trước
    @Test
    void skillScoreHasDiminishingReturnsAndTimelineMatchesProfile() throws Exception {
        long categoryId = createCategory();
        long logic = skillId("LOGIC");
        long strong = createProduct(categoryId, "dr-strong", 3, 12, Map.of(logic, 10));
        long weak = createProduct(categoryId, "dr-weak", 3, 12, Map.of(logic, 2));
        String token = newCustomer();
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();

        call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", strong));
        assertThat(logicScore(token, childId, logic)).isEqualTo(5.0);

        long weakChildProductId = call("POST", "/api/children/" + childId + "/products", token,
                Map.of("productId", weak)).data().path("id").asLong();
        // 5.0 + 2/10 x (10 - 5.0) x 0.5 = 5.5
        assertThat(logicScore(token, childId, logic)).isEqualTo(5.5);

        JsonNode points = call("GET", "/api/children/" + childId + "/skill-profile/timeline", token, null)
                .data().path("points");
        assertThat(points.size()).isEqualTo(2);
        assertThat(logicIn(points.path(0).path("skillScores"), logic)).isEqualTo(5.0);
        assertThat(logicIn(points.path(1).path("skillScores"), logic)).isEqualTo(5.5);

        assertThat(call("DELETE", "/api/children/" + childId + "/products/" + weakChildProductId, token, null)
                .status()).isLessThan(300);
        assertThat(logicScore(token, childId, logic)).isEqualTo(5.0);
    }

    // xem trước: thêm món này thì điểm từng nhóm của bé tăng bao nhiêu; món đã có thì không tính; hồ sơ người khác bị chặn
    @Test
    void previewShowsProjectedGainForChild() throws Exception {
        long categoryId = createCategory();
        long logic = skillId("LOGIC");
        long owned = createProduct(categoryId, "pv-owned", 3, 12, Map.of(logic, 10));
        long candidate = createProduct(categoryId, "pv-candidate", 3, 12, Map.of(logic, 2));
        String token = newCustomer();
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", owned));

        Res preview = call("GET", "/api/children/" + childId + "/skill-profile/preview?productId=" + candidate,
                token, null);
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.data().path("alreadyOwned").asBoolean()).isFalse();
        JsonNode gain = preview.data().path("gains").path(0);
        assertThat(preview.data().path("gains").size()).isEqualTo(1);
        assertThat(gain.path("skillId").asLong()).isEqualTo(logic);
        assertThat(gain.path("currentScore").asDouble()).isEqualTo(5.0);
        assertThat(gain.path("projectedScore").asDouble()).isEqualTo(5.5);
        assertThat(gain.path("gain").asDouble()).isEqualTo(0.5);

        Res ownedPreview = call("GET", "/api/children/" + childId + "/skill-profile/preview?productId=" + owned,
                token, null);
        assertThat(ownedPreview.data().path("alreadyOwned").asBoolean()).isTrue();
        assertThat(ownedPreview.data().path("gains").size()).isZero();

        assertThat(call("GET", "/api/children/" + childId + "/skill-profile/preview?productId=" + candidate,
                newCustomer(), null).status()).isGreaterThanOrEqualTo(400);

        // gợi ý tiếp theo cũng kèm mức tăng dự kiến
        for (JsonNode rec : call("GET", "/api/children/" + childId + "/skill-profile/recommendations?limit=20",
                token, null).data()) {
            assertThat(rec.path("skillGains").isArray()).isTrue();
            if (rec.path("productId").asLong() == candidate) {
                assertThat(rec.path("skillGains").path(0).path("gain").asDouble()).isEqualTo(0.5);
            }
        }
    }

    // chỉ đánh giá được sau khi đơn đã giao, mỗi sản phẩm 1 lần; đánh giá bị ẩn không tính vào điểm
    @Test
    void reviewsRequireDeliveredOrderAndHiddenReviewsAreExcluded() throws Exception {
        long productId = createProduct(createCategory(), "review", 3, 12, Map.of());
        String token = newCustomer();
        long orderId = placeOrder(token, productId);
        Map<String, Object> review = Map.of("rating", 4, "comment", "ok");

        assertThat(call("POST", "/api/products/" + productId + "/reviews", token, review).status()).isEqualTo(400);
        deliver(orderId);
        Res created = call("POST", "/api/products/" + productId + "/reviews", token, review);
        assertThat(created.status()).isEqualTo(201);
        assertThat(call("POST", "/api/products/" + productId + "/reviews", token, review).status()).isEqualTo(400);

        JsonNode detail = call("GET", "/api/products/" + productId, null, null).data();
        assertThat(detail.path("reviewCount").asLong()).isEqualTo(1);
        assertThat(detail.path("averageRating").asDouble()).isEqualTo(4.0);

        long reviewId = created.data().path("id").asLong();
        call("PATCH", "/api/admin/reviews/" + reviewId + "/visibility", adminToken, Map.of("visible", false));
        assertThat(call("GET", "/api/products/" + productId, null, null).data().path("reviewCount").asLong()).isZero();
    }

    // đổi / trả chỉ khi đã giao; chờ -> tiếp nhận/từ chối, đang xử lý -> đã giải quyết;
    // đóng khiếu nại phải có phản hồi; đã đóng thì không mở lại
    @Test
    void complaintRulesAreEnforced() throws Exception {
        long productId = createProduct(createCategory(), "complaint", 3, 12, Map.of());
        String token = newCustomer();
        long orderId = placeOrder(token, productId);
        String complaintsPath = "/api/orders/" + orderId + "/complaints";
        Map<String, Object> exchange = Map.of("type", "EXCHANGE", "content", "Thiếu chi tiết");

        assertThat(submitComplaint(token, orderId, exchange).status()).isEqualTo(400);
        // khách khác không gửi được khiếu nại cho đơn không phải của mình
        assertThat(call("POST", complaintsPath, newCustomer(), Map.of("type", "OTHER",
                "content", "x")).status()).isEqualTo(404);
        deliver(orderId);
        long complaintId = submitComplaint(token, orderId, exchange).data().path("id").asLong();

        // chưa tiếp nhận thì không được đánh dấu đã giải quyết
        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "RESOLVED", "response", "Đã gửi bù")).status()).isEqualTo(400);
        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "PROCESSING")).status()).isEqualTo(200);
        // đã tiếp nhận thì không từ chối được nữa
        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "REJECTED", "response", "Không hợp lệ")).status()).isEqualTo(400);
        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "RESOLVED")).status()).isEqualTo(400);
        Res resolved = call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "RESOLVED", "response", "Đã gửi bù"));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.data().path("handledByName").asString()).isNotBlank();
        assertThat(call("GET", "/api/admin/complaints/" + complaintId, adminToken, null).data().path("status").asString())
                .isEqualTo("RESOLVED");
        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "PROCESSING")).status()).isEqualTo(400);
        assertThat(call("GET", "/api/complaints", token, null).data().path(0).path("response").asString())
                .isEqualTo("Đã gửi bù");
    }

    // link video hướng dẫn: chỉ nhận link YouTube, để trống thì xóa video
    @Test
    void productVideoUrlIsValidatedAndClearable() throws Exception {
        long categoryId = createCategory();
        Map<String, Object> body = productBody(categoryId, "video", 3, 12, Map.of());

        body.put("videoUrl", "https://vimeo.com/123456");
        assertThat(call("POST", "/api/admin/products", adminToken, body).status()).isEqualTo(400);

        body.put("videoUrl", " https://youtu.be/dQw4w9WgXcQ?t=10 ");
        Res created = call("POST", "/api/admin/products", adminToken, body);
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        long productId = created.data().path("id").asLong();
        assertThat(call("GET", "/api/products/" + productId, null, null).data().path("videoUrl").asString())
                .isEqualTo("https://youtu.be/dQw4w9WgXcQ?t=10");

        body.put("videoUrl", "");
        assertThat(call("PUT", "/api/admin/products/" + productId, adminToken, body).status()).isEqualTo(200);
        assertThat(call("GET", "/api/products/" + productId, null, null).data().path("videoUrl").isNull()).isTrue();
    }

    // ảnh tải lên: ảnh đầu là ảnh đại diện, file giả dạng ảnh bị từ chối, sửa sản phẩm không làm mất ảnh
    @Test
    void productImagesAreUploadedAndManagedSeparately() throws Exception {
        long categoryId = createCategory();
        long productId = createProduct(categoryId, "images", 3, 12, Map.of());
        String imagesPath = "/api/admin/products/" + productId + "/images";
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        assertThat(upload(imagesPath, null, "a.png", "image/png", png).status()).isEqualTo(401);
        assertThat(upload(imagesPath, adminToken, "a.png", "image/png", "<?php".getBytes()).status()).isEqualTo(400);
        assertThat(upload(imagesPath, adminToken, "a.gif", "image/gif", png).status()).isEqualTo(400);

        Res first = upload(imagesPath, adminToken, "a.png", "image/png", png);
        assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
        assertThat(first.data().path("thumbnail").asBoolean()).isTrue();
        long firstId = first.data().path("id").asLong();
        Res second = upload(imagesPath, adminToken, "b.png", "image/png", png);
        assertThat(second.data().path("thumbnail").asBoolean()).isFalse();
        long secondId = second.data().path("id").asLong();

        // file được phục vụ công khai, không cần đăng nhập
        HttpResponse<byte[]> served = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + first.data().path("url").asString())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(png);

        assertThat(call("PUT", imagesPath + "/order", adminToken, Map.of("imageIds", List.of(secondId))).status())
                .isEqualTo(400);
        Res reordered = call("PUT", imagesPath + "/order", adminToken, Map.of("imageIds", List.of(secondId, firstId)));
        assertThat(reordered.data().path(0).path("id").asLong()).isEqualTo(secondId);
        assertThat(call("PATCH", imagesPath + "/" + secondId + "/thumbnail", adminToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/products/" + productId, null, null).data().path("images").path(0).path("thumbnail")
                .asBoolean()).isTrue();

        // PUT sản phẩm không đụng tới ảnh
        Res updated = call("PUT", "/api/admin/products/" + productId, adminToken,
                productBody(categoryId, "images", 3, 12, Map.of()));
        assertThat(updated.data().path("images").size()).isEqualTo(2);

        // xóa ảnh đại diện thì ảnh còn lại lên thay
        assertThat(call("DELETE", imagesPath + "/" + secondId, adminToken, null).status()).isEqualTo(200);
        Res remaining = call("GET", imagesPath, adminToken, null);
        assertThat(remaining.data().size()).isEqualTo(1);
        assertThat(remaining.data().path(0).path("thumbnail").asBoolean()).isTrue();
    }

    // danh mục còn sản phẩm / nhóm kỹ năng đang dùng thì không xóa được; mã nhóm kỹ năng không đổi được
    @Test
    void catalogDeletionGuards() throws Exception {
        long categoryId = createCategory();
        createProduct(categoryId, "guard", 3, 12, Map.of());
        assertThat(call("DELETE", "/api/admin/categories/" + categoryId, adminToken, null).status()).isEqualTo(400);
        assertThat(call("DELETE", "/api/admin/skills/" + skillId("LOGIC"), adminToken, null).status()).isEqualTo(400);

        String code = "TEMP_" + (char) ('A' + SEQ.incrementAndGet() % 26) + "_SKILL";
        Res skill = call("POST", "/api/admin/skills", adminToken, Map.of("code", code, "name", "Tạm"));
        assertThat(skill.status()).isEqualTo(201);
        long id = skill.data().path("id").asLong();
        assertThat(call("PUT", "/api/admin/skills/" + id, adminToken, Map.of("code", "OTHER_CODE", "name", "x"))
                .status()).isEqualTo(400);
        assertThat(call("DELETE", "/api/admin/skills/" + id, adminToken, null).status()).isEqualTo(200);
    }

    @Test
    void statsAreAdminOnly() throws Exception {
        Res stats = call("GET", "/api/admin/stats", adminToken, null);
        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.data().path("dailyOrders").size()).isEqualTo(14);
        assertThat(call("GET", "/api/admin/stats", newCustomer(), null).status()).isEqualTo(403);
    }

    // tìm kiếm bỏ qua dấu tiếng Việt và hoa thường
    @Test
    void searchIgnoresVietnameseDiacritics() throws Exception {
        String tag = "zq" + SEQ.incrementAndGet();
        long productId = createProduct(createCategory(), "Bộ Lắp Ráp Đặc Biệt " + tag, 3, 12, Map.of());
        assertThat(ids(call("GET", "/api/products?keyword=lap%20rap%20dac%20biet%20" + tag, null, null)
                .data().path("content"))).containsExactly(productId);
        assertThat(ids(call("GET", "/api/products?keyword=" + URLEncoder.encode("LẮP RÁP đặc biệt " + tag,
                StandardCharsets.UTF_8), null, null).data().path("content"))).containsExactly(productId);
    }

    // gợi ý khi gõ: so khớp không dấu nhưng trả về tên gốc có dấu, khớp đầu tên xếp trước, bỏ sản phẩm đã ẩn
    @Test
    void suggestionsMatchWithoutDiacriticsAndKeepOriginalName() throws Exception {
        String tag = "gy" + SEQ.incrementAndGet();
        long categoryId = createCategory();
        long inside = createProduct(categoryId, "Bộ Lắp Ráp Gợi Ý " + tag, 3, 12, Map.of());
        long prefix = createProduct(categoryId, tag + " Lắp Ráp Gợi Ý", 3, 12, Map.of());
        long hidden = createProduct(categoryId, "Lắp Ráp Gợi Ý Đã Ẩn " + tag, 3, 12, Map.of());
        assertThat(call("DELETE", "/api/admin/products/" + hidden, adminToken, null).status()).isEqualTo(200);

        JsonNode plain = call("GET", "/api/products/suggestions?q=lap%20rap%20goi%20y%20" + tag, null, null).data();
        List<Long> productIds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        plain.forEach(s -> {
            if (s.path("type").asString().equals("PRODUCT")) {
                productIds.add(s.path("id").asLong());
                labels.add(s.path("label").asString());
            }
        });
        assertThat(productIds).containsExactlyInAnyOrder(inside, prefix);
        assertThat(labels).contains("[TEST] Bộ Lắp Ráp Gợi Ý " + tag);

        // "[TEST] gy.. lắp" bắt đầu bằng từ khớp, nằm trước món chỉ khớp giữa tên
        JsonNode byTag = call("GET", "/api/products/suggestions?q=" + tag, null, null).data();
        assertThat(byTag.get(0).path("id").asLong()).isEqualTo(prefix);

        JsonNode skill = call("GET", "/api/products/suggestions?q=sang%20t", null, null).data();
        assertThat(skill.toString()).contains("\"type\":\"SKILL\"", "\"code\":\"CREATIVE\"", "Sáng tạo");
        assertThat(call("GET", "/api/products/suggestions?q=%20", null, null).data().size()).isZero();
    }

    // "Hợp với bé": theo tuổi bé, bỏ món bé đã có, sắp theo mức bổ sung; xem trước cả giỏ
    @Test
    void productMatchesFollowChildAndBundlePreviewSkipsOwned() throws Exception {
        long categoryId = createCategory();
        long logic = skillId("LOGIC");
        long creative = skillId("CREATIVE");
        long owned = createProduct(categoryId, "pm-owned", 3, 12, Map.of(logic, 10));
        long creativeToy = createProduct(categoryId, "pm-creative", 3, 12, Map.of(creative, 10));
        long logicToy = createProduct(categoryId, "pm-logic", 3, 12, Map.of(logic, 2));
        createProduct(categoryId, "pm-too-old", 10, 14, Map.of(creative, 10));
        String token = newCustomer();
        // sinh 2019-01-01 -> 7 tuổi
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", owned));

        JsonNode matches = call("GET", "/api/children/" + childId + "/product-matches?categoryId=" + categoryId,
                token, null).data().path("content");
        List<Long> matchIds = new ArrayList<>();
        matches.forEach(m -> matchIds.add(m.path("product").path("id").asLong()));
        // Sáng tạo đang 0 nên +5,0; Logic đang 5,0 nên món 2 điểm chỉ +0,5
        assertThat(matchIds).containsExactly(creativeToy, logicToy);
        assertThat(matches.path(0).path("fitScore").asDouble()).isEqualTo(5.0);
        assertThat(matches.path(1).path("fitScore").asDouble()).isEqualTo(0.5);

        Res bundle = call("GET", "/api/children/" + childId + "/skill-profile/preview-bundle?productIds="
                + owned + "," + creativeToy + "," + logicToy, token, null);
        assertThat(bundle.status()).isEqualTo(200);
        List<Long> ownedIds = new ArrayList<>();
        bundle.data().path("alreadyOwnedProductIds").forEach(id -> ownedIds.add(id.asLong()));
        assertThat(ownedIds).containsExactly(owned);
        Map<Long, Double> projected = new HashMap<>();
        bundle.data().path("gains").forEach(g ->
                projected.put(g.path("skillId").asLong(), g.path("projectedScore").asDouble()));
        assertThat(projected.get(logic)).isEqualTo(5.5);
        assertThat(projected.get(creative)).isEqualTo(5.0);

        assertThat(call("GET", "/api/children/" + childId + "/product-matches", newCustomer(), null).status())
                .isGreaterThanOrEqualTo(400);
    }

    // khách nhận thông báo khi đơn đổi trạng thái / hồ sơ bé thay đổi / khiếu nại được xử lý;
    // admin nhận thông báo đơn mới, khiếu nại mới, đánh giá mới
    @Test
    void notificationsReachCustomerAndAdmins() throws Exception {
        long logic = skillId("LOGIC");
        long productId = createProduct(createCategory(), "notify", 3, 12, Map.of(logic, 8));
        String token = newCustomer();
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        call("PATCH", "/api/notifications/read-all", adminToken, null);

        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        Res order = call("POST", "/api/orders", token, Map.of("receiverName", "Test", "receiverPhone", "0901234567",
                "shippingAddress", "1 Test", "paymentMethod", "COD",
                "childAssignments", List.of(Map.of("productId", productId, "childProfileId", childId))));
        assertThat(order.status()).isEqualTo(201);
        assertThat(unread(adminToken).path("byType").path("NEW_ORDER").asLong()).isEqualTo(1);
        deliver(order.data().path("id").asLong());

        // xác nhận, đang giao, đã giao + hồ sơ kỹ năng của bé
        JsonNode counts = unread(token);
        assertThat(counts.path("total").asLong()).isEqualTo(4);
        assertThat(counts.path("byType").path("ORDER_STATUS").asLong()).isEqualTo(3);
        JsonNode latest = call("GET", "/api/notifications?size=1", token, null).data().path("content").path(0);
        assertThat(latest.path("type").asString()).isEqualTo("SKILL_PROFILE_UPDATED");
        assertThat(latest.path("message").asString()).contains("Tư duy Logic +4,0");
        assertThat(latest.path("link").asString()).isEqualTo("/children/" + childId);

        // người khác không đánh dấu được thông báo của mình
        long latestId = latest.path("id").asLong();
        call("PATCH", "/api/notifications/" + latestId + "/read", newCustomer(), null);
        assertThat(unread(token).path("total").asLong()).isEqualTo(4);
        call("PATCH", "/api/notifications/" + latestId + "/read", token, null);
        assertThat(unread(token).path("total").asLong()).isEqualTo(3);

        long orderId = order.data().path("id").asLong();
        // khiếu nại chất lượng không bắt buộc video
        long complaintId = call("POST", "/api/orders/" + orderId + "/complaints", token,
                Map.of("type", "QUALITY", "content", "Thiếu chi tiết")).data().path("id").asLong();
        assertThat(call("POST", "/api/products/" + productId + "/reviews", token, Map.of("rating", 5)).status())
                .isEqualTo(201);
        JsonNode adminCounts = unread(adminToken).path("byType");
        assertThat(adminCounts.path("NEW_COMPLAINT").asLong()).isEqualTo(1);
        assertThat(adminCounts.path("NEW_REVIEW").asLong()).isEqualTo(1);

        // admin mở trang Đánh giá: chỉ các thông báo đánh giá được đánh dấu đã đọc
        call("PATCH", "/api/notifications/read-all?types=NEW_REVIEW", adminToken, null);
        adminCounts = unread(adminToken).path("byType");
        assertThat(adminCounts.has("NEW_REVIEW")).isFalse();
        assertThat(adminCounts.path("NEW_COMPLAINT").asLong()).isEqualTo(1);

        call("PATCH", "/api/admin/complaints/" + complaintId, adminToken, Map.of("status", "PROCESSING"));
        assertThat(unread(token).path("byType").path("COMPLAINT_UPDATED").asLong()).isEqualTo(1);
        call("PATCH", "/api/notifications/read-all", token, null);
        assertThat(unread(token).path("total").asLong()).isZero();
    }

    // trả hàng: duyệt xong khách có 7 ngày gửi hàng về; quá hạn thì không duyệt hoàn tất được
    // và yêu cầu tự bị từ chối, đánh dấu do khách trễ hạn
    @Test
    void approvedReturnExpiresAfterSevenDays() throws Exception {
        long productId = createProduct(createCategory(), "return-window", 3, 12, Map.of());
        String token = newCustomer();
        long orderId = placeOrder(token, productId);
        deliver(orderId);
        long complaintId = submitComplaint(token, orderId, Map.of("type", "RETURN", "content", "Muốn trả hàng"))
                .data().path("id").asLong();

        JsonNode accepted = call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "PROCESSING")).data();
        LocalDateTime acceptedAt = LocalDateTime.parse(accepted.path("acceptedAt").asString());
        assertThat(LocalDateTime.parse(accepted.path("returnDeadline").asString())).isEqualTo(acceptedAt.plusDays(7));
        assertThat(adminComplaintService.expireOverdueReturns()).isZero();

        // giả lập đã duyệt từ 8 ngày trước
        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
        complaint.setAcceptedAt(LocalDateTime.now().minusDays(8));
        complaintRepository.save(complaint);

        assertThat(call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "RESOLVED", "response", "Đã nhận hàng")).status()).isEqualTo(400);
        assertThat(adminComplaintService.expireOverdueReturns()).isEqualTo(1);

        JsonNode mine = call("GET", "/api/complaints", token, null).data().path(0);
        assertThat(mine.path("status").asString()).isEqualTo("REJECTED");
        assertThat(mine.path("returnExpired").asBoolean()).isTrue();
        assertThat(mine.path("response").asString()).startsWith("Quá hạn gửi hàng về");
        assertThat(call("GET", "/api/notifications?size=1", token, null).data().path("content").path(0)
                .path("title").asString()).isEqualTo("Yêu cầu trả hàng đã hết hạn");
        // đã đóng thì lần chạy sau không đụng lại
        assertThat(adminComplaintService.expireOverdueReturns()).isZero();
    }

    // đổi / trả / chất lượng bắt buộc video mở hàng; file giả đuôi bị chặn; chỉ chủ khiếu nại và admin xem được file;
    // đổi hàng cũng có hạn 7 ngày gửi hàng về
    @Test
    void evidenceIsRequiredAndPrivate() throws Exception {
        long productId = createProduct(createCategory(), "evidence", 3, 12, Map.of());
        String token = newCustomer();
        long orderId = placeOrder(token, productId);
        deliver(orderId);
        String path = "/api/orders/" + orderId + "/complaints";
        Map<String, Object> exchange = Map.of("type", "EXCHANGE", "content", "Hộp bị móp, thiếu chi tiết");
        Part request = new Part("request", null, "application/json", JSON.writeValueAsBytes(exchange));

        // không có video mở hàng: JSON hay multipart đều bị từ chối
        assertThat(call("POST", path, token, exchange).status()).isEqualTo(400);
        assertThat(multipart(path, token, List.of(request)).status()).isEqualTo(400);
        // file đổi đuôi .mp4 nhưng không phải video
        assertThat(multipart(path, token, List.of(request, new Part("unboxingVideo", "fake.mp4", "video/mp4",
                "not a video at all".getBytes(StandardCharsets.UTF_8)))).status()).isEqualTo(400);
        // phản hồi khác không cần video; khiếu nại chất lượng chỉ kèm ảnh / video minh họa tùy chọn
        assertThat(call("POST", path, token, Map.of("type", "OTHER", "content", "Giao hơi chậm")).status())
                .isEqualTo(201);
        Res quality = multipart(path, token, List.of(
                new Part("request", null, "application/json",
                        JSON.writeValueAsBytes(Map.of("type", "QUALITY", "content", "Bánh răng bị kẹt"))),
                new Part("conditionFiles", "banh-rang.png", "image/png", FAKE_PNG)));
        assertThat(quality.status()).as(quality.body().toString()).isEqualTo(201);
        assertThat(quality.data().path("attachments").path(0).path("kind").asString()).isEqualTo("CONDITION");

        Res created = multipart(path, token, List.of(request,
                new Part("unboxingVideo", "mo-hang.mp4", "video/mp4", FAKE_MP4),
                new Part("conditionFiles", "hop-mop.png", "image/png", FAKE_PNG)));
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        long complaintId = created.data().path("id").asLong();
        JsonNode attachments = created.data().path("attachments");
        assertThat(attachments.size()).isEqualTo(2);
        assertThat(attachments.path(0).path("kind").asString()).isEqualTo("UNBOXING_VIDEO");
        assertThat(attachments.path(1).path("kind").asString()).isEqualTo("CONDITION");

        String videoUrl = baseUrl + "/api/complaints/" + complaintId + "/attachments"
                + "/" + attachments.path(0).path("id").asLong();
        HttpResponse<byte[]> own = download(videoUrl, token);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(own.headers().firstValue("Content-Type")).hasValue("video/mp4");
        assertThat(own.body()).isEqualTo(FAKE_MP4);
        assertThat(download(videoUrl, adminToken).statusCode()).isEqualTo(200);
        assertThat(download(videoUrl, newCustomer()).statusCode()).isEqualTo(404);
        assertThat(download(videoUrl, null).statusCode()).isIn(401, 403);

        JsonNode accepted = call("PATCH", "/api/admin/complaints/" + complaintId, adminToken,
                Map.of("status", "PROCESSING")).data();
        assertThat(accepted.path("returnDeadline").isNull()).isFalse();
    }

    // bằng chứng tự xóa 30 ngày sau khi yêu cầu đóng, trừ khi admin giữ lại; yêu cầu còn mở không bị xóa;
    // bản ghi file vẫn còn để thống kê
    @Test
    void closedComplaintEvidenceIsPurgedAfterRetentionUnlessHeld() throws Exception {
        long productId = createProduct(createCategory(), "purge", 3, 12, Map.of());
        String token = newCustomer();
        long closedId = deliveredComplaint(token, productId);
        long heldId = deliveredComplaint(token, productId);
        long openId = deliveredComplaint(token, productId);
        for (long id : List.of(closedId, heldId)) {
            call("PATCH", "/api/admin/complaints/" + id, adminToken, Map.of("status", "PROCESSING"));
            call("PATCH", "/api/admin/complaints/" + id, adminToken, Map.of("status", "RESOLVED", "response", "Đã đổi"));
        }
        JsonNode closed = call("GET", "/api/admin/complaints/" + closedId, adminToken, null).data();
        assertThat(LocalDateTime.parse(closed.path("evidencePurgeAt").asString()))
                .isEqualTo(LocalDateTime.parse(closed.path("handledAt").asString()).plusDays(30));
        JsonNode held = call("PATCH", "/api/admin/complaints/" + heldId + "/evidence-hold", adminToken,
                Map.of("hold", true)).data();
        assertThat(held.path("evidenceHold").asBoolean()).isTrue();
        assertThat(held.path("evidencePurgeAt").isNull()).isTrue();

        // giả lập đã đóng 31 ngày trước
        for (long id : List.of(closedId, heldId)) {
            Complaint complaint = complaintRepository.findById(id).orElseThrow();
            complaint.setHandledAt(LocalDateTime.now().minusDays(31));
            complaintRepository.save(complaint);
        }
        assertThat(adminComplaintService.purgeExpiredEvidence()).isEqualTo(1);

        JsonNode purged = call("GET", "/api/admin/complaints/" + closedId, adminToken, null).data();
        JsonNode file = purged.path("attachments").path(0);
        assertThat(file.path("purgedAt").isNull()).isFalse();
        assertThat(file.path("originalName").asString()).isEqualTo("mo-hang.mp4");
        assertThat(purged.path("evidencePurgeAt").isNull()).isTrue();
        assertThat(download(baseUrl + "/api/complaints/" + closedId + "/attachments/" + file.path("id").asLong(),
                token).statusCode()).isEqualTo(404);

        for (long id : List.of(heldId, openId)) {
            long attachmentId = call("GET", "/api/admin/complaints/" + id, adminToken, null).data()
                    .path("attachments").path(0).path("id").asLong();
            assertThat(download(baseUrl + "/api/complaints/" + id + "/attachments/" + attachmentId, token)
                    .statusCode()).isEqualTo(200);
        }
        // đã dọn thì lần chạy sau không đụng lại
        assertThat(adminComplaintService.purgeExpiredEvidence()).isZero();
    }

    // =================================== helpers =======================================

    // đơn mới đã giao + yêu cầu đổi hàng kèm video mở hàng; trả id yêu cầu
    private long deliveredComplaint(String token, long productId) throws Exception {
        long orderId = placeOrder(token, productId);
        deliver(orderId);
        Res res = submitComplaint(token, orderId, Map.of("type", "EXCHANGE", "content", "Hộp bị móp"));
        assertThat(res.status()).as(res.body().toString()).isEqualTo(201);
        return res.data().path("id").asLong();
    }

    private HttpResponse<byte[]> download(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode unread(String token) throws Exception {
        return call("GET", "/api/notifications/unread-count", token, null).data();
    }

    private double logicScore(String token, long childId, long logicId) throws Exception {
        return logicIn(call("GET", "/api/children/" + childId + "/skill-profile", token, null).data()
                .path("skillScores"), logicId);
    }

    private double logicIn(JsonNode skillScores, long logicId) {
        for (JsonNode s : skillScores) {
            if (s.path("skillId").asLong() == logicId) {
                return s.path("score").asDouble();
            }
        }
        return -1;
    }

    private long placeOrder(String token, long productId) throws Exception {
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        Res order = call("POST", "/api/orders", token, Map.of("receiverName", "Test", "receiverPhone", "0901234567",
                "shippingAddress", "1 Test", "paymentMethod", "COD"));
        assertThat(order.status()).isEqualTo(201);
        return order.data().path("id").asLong();
    }

    private void deliver(long orderId) throws Exception {
        for (String status : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            assertThat(call("PATCH", "/api/admin/orders/" + orderId + "/status", adminToken, Map.of("status", status))
                    .status()).isEqualTo(200);
        }
    }

    private long createCategory() throws Exception {
        Res res = call("POST", "/api/admin/categories", adminToken,
                Map.of("name", "cat-" + SEQ.incrementAndGet() + "-" + System.nanoTime()));
        assertThat(res.status()).isEqualTo(201);
        return res.data().path("id").asLong();
    }

    private long createProduct(long categoryId, String name, int minAge, int maxAge, Map<Long, Integer> impacts)
            throws Exception {
        Res res = call("POST", "/api/admin/products", adminToken,
                productBody(categoryId, name, minAge, maxAge, impacts));
        assertThat(res.status()).as(res.body().toString()).isEqualTo(201);
        return res.data().path("id").asLong();
    }

    private Map<String, Object> productBody(long categoryId, String name, int minAge, int maxAge,
                                            Map<Long, Integer> impacts) {
        List<Map<String, Object>> impactList = new ArrayList<>();
        impacts.forEach((skillId, value) -> impactList.add(Map.of("skillId", skillId, "impactIndex", value)));
        Map<String, Object> body = new HashMap<>();
        body.put("name", "[TEST] " + name);
        body.put("price", 100000);
        body.put("stockQuantity", 10);
        body.put("minAge", minAge);
        body.put("maxAge", maxAge);
        body.put("categoryId", categoryId);
        body.put("skillImpacts", impactList);
        return body;
    }

    private long skillId(String code) throws Exception {
        for (JsonNode s : call("GET", "/api/skills", null, null).data()) {
            if (s.path("code").asString().equals(code)) {
                return s.path("id").asLong();
            }
        }
        throw new IllegalStateException("Skill not seeded: " + code);
    }

    private List<Long> ids(JsonNode content) {
        List<Long> ids = new ArrayList<>();
        content.forEach(p -> ids.add(p.path("id").asLong()));
        return ids;
    }

    private Res call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body() == null || r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
        return new Res(r.statusCode(), node);
    }

    // multipart/form-data một field "file"
    // vài byte đầu đủ để qua kiểm tra chữ ký: MP4 có "ftyp" ở offset 4, PNG mở đầu bằng 0x89 PNG
    private static final byte[] FAKE_MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
    private static final byte[] FAKE_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};

    record Part(String name, String fileName, String contentType, byte[] content) {
    }

    // khiếu nại kèm video mở hàng (đổi / trả / chất lượng bắt buộc có video)
    private Res submitComplaint(String token, long orderId, Map<String, Object> body) throws Exception {
        return multipart("/api/orders/" + orderId + "/complaints", token, List.of(
                new Part("request", null, "application/json", JSON.writeValueAsBytes(body)),
                new Part("unboxingVideo", "mo-hang.mp4", "video/mp4", FAKE_MP4)));
    }

    private Res multipart(String path, String token, List<Part> parts) throws Exception {
        String boundary = "----test" + System.nanoTime();
        List<byte[]> chunks = new ArrayList<>();
        for (Part part : parts) {
            String disposition = "form-data; name=\"" + part.name() + "\""
                    + (part.fileName() == null ? "" : "; filename=\"" + part.fileName() + "\"");
            chunks.add(("--" + boundary + "\r\nContent-Disposition: " + disposition + "\r\nContent-Type: "
                    + part.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            chunks.add(part.content());
            chunks.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        chunks.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(chunks));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body() == null || r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
        return new Res(r.statusCode(), node);
    }

    private Res upload(String path, String token, String fileName, String contentType, byte[] content) throws Exception {
        String boundary = "----test" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes();
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, content, tail)));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body() == null || r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
        return new Res(r.statusCode(), node);
    }

    private String login(String email, String password) throws Exception {
        Res r = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(r.status()).as("login " + email).isEqualTo(200);
        return r.data().path("token").asString();
    }

    private String newCustomer() throws Exception {
        String email = "cf" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Test User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        return login(email, PASSWORD);
    }
}
