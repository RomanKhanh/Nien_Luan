package com.brainblocks.backend.regression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        assertThat(logicScore(token, childId, logic)).isEqualTo(8);

        Res updated = call("PUT", "/api/admin/products/" + productId, adminToken, productBody(categoryId, "recalc",
                3, 12, Map.of(logic, 3)));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(logicScore(token, childId, logic)).isEqualTo(3);

        assertThat(call("DELETE", "/api/admin/products/" + productId, adminToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/products/" + productId, null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/admin/products/" + productId, adminToken, null).status()).isEqualTo(200);
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

    // đổi / trả chỉ khi đã giao; đóng khiếu nại phải có phản hồi; đã đóng thì không mở lại
    @Test
    void complaintRulesAreEnforced() throws Exception {
        long productId = createProduct(createCategory(), "complaint", 3, 12, Map.of());
        String token = newCustomer();
        long orderId = placeOrder(token, productId);
        String complaintsPath = "/api/orders/" + orderId + "/complaints";
        Map<String, Object> exchange = Map.of("type", "EXCHANGE", "content", "Thiếu chi tiết");

        assertThat(call("POST", complaintsPath, token, exchange).status()).isEqualTo(400);
        // khách khác không gửi được khiếu nại cho đơn không phải của mình
        assertThat(call("POST", complaintsPath, newCustomer(), Map.of("type", "OTHER",
                "content", "x")).status()).isEqualTo(404);
        deliver(orderId);
        long complaintId = call("POST", complaintsPath, token, exchange).data().path("id").asLong();

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

    // =================================== helpers =======================================

    private long logicScore(String token, long childId, long logicId) throws Exception {
        for (JsonNode s : call("GET", "/api/children/" + childId + "/skill-profile", token, null).data().path("skillScores")) {
            if (s.path("skillId").asLong() == logicId) {
                return Math.round(s.path("score").asDouble());
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
