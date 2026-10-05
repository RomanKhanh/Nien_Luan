package com.brainblocks.backend.regression;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Voucher: quà tạo tài khoản, áp voucher khi đặt hàng (1 freeship + 1 giảm giá), trả voucher khi hủy đơn,
 * quà mốc kỹ năng của bé khi đơn giao xong. Gọi HTTP thật vào app (RANDOM_PORT) trên H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VoucherTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, Object> ORDER_BODY = Map.of(
            "receiverName", "Voucher", "receiverPhone", "0901234567",
            "provinceCode", 92, "districtCode", 916, "wardCode", 31117,
            "addressDetail", "123 Voucher", "paymentMethod", "COD");

    @Autowired
    private Environment environment;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager em;

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

    // tạo tài khoản: 1 freeship (tiền hàng từ 200k), 1 giảm 10% (từ 200k, tối đa 150k), 1 giảm 50k (không tối thiểu)
    @Test
    void newAccountGetsWelcomeVouchers() throws Exception {
        String token = newCustomer();
        JsonNode vouchers = call("GET", "/api/vouchers", token, null).data();
        assertThat(vouchers.size()).isEqualTo(3);
        Map<String, JsonNode> byLabel = byLabel(vouchers);
        assertThat(byLabel).containsOnlyKeys("Miễn phí vận chuyển", "Giảm 10%", "Giảm 50.000₫");
        assertThat(byLabel.get("Miễn phí vận chuyển").path("minSubtotal").asInt()).isEqualTo(200000);
        assertThat(byLabel.get("Giảm 10%").path("maxDiscount").asInt()).isEqualTo(150000);
        assertThat(byLabel.get("Giảm 50.000₫").path("minSubtotal").asInt()).isZero();
        vouchers.forEach(v -> {
            assertThat(v.path("status").asString()).isEqualTo("AVAILABLE");
            assertThat(v.path("reason").asString()).isEqualTo("WELCOME");
        });
    }

    // đơn 300k: freeship trừ hết phí ship, 10% trừ 30k; voucher đã dùng không dùng lại được; hủy đơn thì trả lại
    @Test
    void vouchersApplyToOrderAndComeBackWhenCancelled() throws Exception {
        String token = newCustomer();
        Map<String, JsonNode> vouchers = byLabel(call("GET", "/api/vouchers", token, null).data());
        long freeship = vouchers.get("Miễn phí vận chuyển").path("id").asLong();
        long percent = vouchers.get("Giảm 10%").path("id").asLong();
        long productId = createProduct("voucher-order", 20, null);

        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 3));
        Res order = call("POST", "/api/orders", token, withVouchers(freeship, percent));
        assertThat(order.status()).isEqualTo(201);
        JsonNode o = order.data();
        int fee = o.path("shippingFee").asInt();
        assertThat(fee).isPositive();
        assertThat(o.path("shippingDiscount").asInt()).isEqualTo(fee);
        assertThat(o.path("discountAmount").asInt()).isEqualTo(30000);
        assertThat(o.path("totalAmount").asInt()).isEqualTo(270000);

        Map<String, JsonNode> after = byLabel(call("GET", "/api/vouchers", token, null).data());
        assertThat(after.get("Giảm 10%").path("status").asString()).isEqualTo("USED");
        assertThat(after.get("Giảm 10%").path("orderCode").asString()).isEqualTo(o.path("orderCode").asString());

        // dùng lại voucher đã dùng: lỗi, giỏ vẫn còn nguyên
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 3));
        assertThat(call("POST", "/api/orders", token, withVouchers(null, percent)).status()).isEqualTo(400);
        // voucher giảm giá đặt vào ô freeship: lỗi
        long amount = vouchers.get("Giảm 50.000₫").path("id").asLong();
        assertThat(call("POST", "/api/orders", token, withVouchers(amount, null)).status()).isEqualTo(400);

        // hủy đơn: voucher dùng được lại
        assertThat(call("PATCH", "/api/orders/" + o.path("id").asLong() + "/cancel", token, null).status())
                .isEqualTo(200);
        Map<String, JsonNode> released = byLabel(call("GET", "/api/vouchers", token, null).data());
        assertThat(released.get("Giảm 10%").path("status").asString()).isEqualTo("AVAILABLE");
        assertThat(released.get("Miễn phí vận chuyển").path("status").asString()).isEqualTo("AVAILABLE");
    }

    // freeship cần tiền hàng từ 200k; giảm 50k không cần mức tối thiểu và không giảm quá tiền hàng
    @Test
    void minimumSubtotalIsEnforced() throws Exception {
        String token = newCustomer();
        Map<String, JsonNode> vouchers = byLabel(call("GET", "/api/vouchers", token, null).data());
        long productId = createProduct("voucher-min", 20, null);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));

        long freeship = vouchers.get("Miễn phí vận chuyển").path("id").asLong();
        assertThat(call("POST", "/api/orders", token, withVouchers(freeship, null)).status()).isEqualTo(400);

        long amount = vouchers.get("Giảm 50.000₫").path("id").asLong();
        Res order = call("POST", "/api/orders", token, withVouchers(null, amount));
        assertThat(order.status()).isEqualTo(201);
        assertThat(order.data().path("discountAmount").asInt()).isEqualTo(50000);
        assertThat(order.data().path("totalAmount").asInt())
                .isEqualTo(50000 + order.data().path("shippingFee").asInt());
    }

    // voucher của khách khác coi như không tồn tại
    @Test
    void cannotUseAnotherCustomersVoucher() throws Exception {
        String owner = newCustomer();
        long percent = byLabel(call("GET", "/api/vouchers", owner, null).data()).get("Giảm 10%").path("id").asLong();
        String other = newCustomer();
        long productId = createProduct("voucher-other", 20, null);
        call("POST", "/api/cart/items", other, Map.of("productId", productId, "quantity", 3));
        assertThat(call("POST", "/api/orders", other, withVouchers(null, percent)).status()).isEqualTo(404);
    }

    // Mốc kỹ năng của bé chỉ tính đồ đã mua: tự thêm đồ không được quà; đơn giao xong đưa 2 nhóm lên "Đang phát
    // triển" thì nhận quà của cả mốc "Mới bắt đầu" và "Đang phát triển"; lên "Phong phú" nhận thêm; không tặng trùng
    @Test
    void skillMilestonesGrantVouchersOncePerChild() throws Exception {
        String suffix = String.valueOf(SEQ.incrementAndGet() + System.nanoTime());
        long logic = createSkill("VOU_LOGIC_" + suffix);
        long creative = createSkill("VOU_CREATIVE_" + suffix);
        String token = newCustomer();
        long childId = call("POST", "/api/children", token, Map.of("name", "Kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();

        // 1 món 8 điểm mỗi nhóm -> 4,0 = "Đang phát triển"
        long manual = createProduct("voucher-manual", 20, Map.of(logic, 8, creative, 8));
        assertThat(call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", manual))
                .status()).isEqualTo(201);
        assertThat(call("GET", "/api/vouchers", token, null).data().size()).isEqualTo(3);

        long first = createProduct("voucher-first", 20, Map.of(logic, 8, creative, 8));
        deliverFor(token, childId, first);
        List<String> reasons = reasons(call("GET", "/api/vouchers", token, null).data());
        assertThat(reasons).containsExactlyInAnyOrder("WELCOME", "WELCOME", "WELCOME",
                "SKILL_BEGINNER", "SKILL_DEVELOPING", "SKILL_DEVELOPING");

        // thêm 1 món 10 điểm mỗi nhóm -> 1 - 0,6 x 0,5 = 7,0 = "Phong phú"
        long second = createProduct("voucher-second", 20, Map.of(logic, 10, creative, 10));
        deliverFor(token, childId, second);
        JsonNode vouchers = call("GET", "/api/vouchers", token, null).data();
        assertThat(reasons(vouchers)).filteredOn("SKILL_RICH"::equals).hasSize(3);
        assertThat(vouchers.size()).isEqualTo(9);
        vouchers.forEach(v -> {
            if (v.path("reason").asString().startsWith("SKILL_")) {
                assertThat(v.path("childName").asString()).isEqualTo("Kid");
            }
        });

        // giao thêm đơn nữa: không tặng trùng
        long third = createProduct("voucher-third", 20, Map.of(logic, 5));
        deliverFor(token, childId, third);
        assertThat(call("GET", "/api/vouchers", token, null).data().size()).isEqualTo(9);

        JsonNode summary = call("GET", "/api/admin/vouchers/summary", adminToken, null).data();
        assertThat(summary.path("issued").asLong()).isGreaterThanOrEqualTo(9);
        assertThat(call("GET", "/api/admin/vouchers?status=AVAILABLE", adminToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/admin/vouchers", token, null).status()).isEqualTo(403);
    }

    // ===== helpers =====

    private void deliverFor(String token, long childId, long productId) throws Exception {
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        Map<String, Object> body = new HashMap<>(ORDER_BODY);
        body.put("childAssignments", List.of(Map.of("productId", productId, "childProfileId", childId)));
        Res order = call("POST", "/api/orders", token, body);
        assertThat(order.status()).isEqualTo(201);
        long orderId = order.data().path("id").asLong();
        for (String status : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            assertThat(call("PATCH", "/api/admin/orders/" + orderId + "/status", adminToken,
                    Map.of("status", status)).status()).isEqualTo(200);
        }
    }

    private static Map<String, Object> withVouchers(Long shippingVoucherId, Long discountVoucherId) {
        Map<String, Object> body = new HashMap<>(ORDER_BODY);
        if (shippingVoucherId != null) {
            body.put("shippingVoucherId", shippingVoucherId);
        }
        if (discountVoucherId != null) {
            body.put("discountVoucherId", discountVoucherId);
        }
        return body;
    }

    private static Map<String, JsonNode> byLabel(JsonNode vouchers) {
        Map<String, JsonNode> byLabel = new HashMap<>();
        vouchers.forEach(v -> byLabel.put(v.path("label").asString(), v));
        return byLabel;
    }

    private static List<String> reasons(JsonNode vouchers) {
        List<String> reasons = new ArrayList<>();
        vouchers.forEach(v -> reasons.add(v.path("reason").asString()));
        return reasons;
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

    private String login(String email, String password) throws Exception {
        Res r = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(r.status()).as("login " + email).isEqualTo(200);
        return r.data().path("token").asString();
    }

    private String newCustomer() throws Exception {
        String email = "voucher" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Voucher User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        return login(email, PASSWORD);
    }

    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private long createSkill(String code) {
        return inTx(() -> {
            Skill skill = Skill.builder().code(code).name(code).build();
            em.persist(skill);
            return skill.getId();
        });
    }

    // giá 100.000₫ mỗi món
    private long createProduct(String name, int stock, Map<Long, Integer> impacts) {
        return inTx(() -> {
            Category category = Category.builder().name("vou-" + name + "-" + System.nanoTime()).build();
            em.persist(category);
            Product product = Product.builder()
                    .name("[VOU] " + name).price(new BigDecimal("100000.00"))
                    .stockQuantity(stock).minAge(3).maxAge(12).category(category)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            if (impacts != null) {
                impacts.forEach((skillId, value) -> product.getProductSkillImpacts().add(ProductSkillImpact.builder()
                        .product(product).skill(em.getReference(Skill.class, skillId)).impactIndex(value).build()));
            }
            em.persist(product);
            return product.getId();
        });
    }
}
