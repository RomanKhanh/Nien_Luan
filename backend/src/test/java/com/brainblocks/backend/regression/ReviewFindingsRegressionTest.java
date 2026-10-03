package com.brainblocks.backend.regression;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.enums.Role;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test hồi quy cho các lỗi tìm được khi review giỏ hàng / đơn hàng / xác thực.
 * Gọi HTTP thật vào app (RANDOM_PORT) trên H2; mỗi test tự tạo dữ liệu riêng nên chạy độc lập.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.brainblocks.backend.regression.SqlRecorder"
)
@ActiveProfiles("test")
class ReviewFindingsRegressionTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, Object> ORDER_BODY = Map.of(
            "receiverName", "Regression", "receiverPhone", "0901234567",
            "provinceCode", 92, "districtCode", 916, "wardCode", 31117,
            "addressDetail", "123 Regression", "paymentMethod", "COD");

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

    // 1. cộng dồn số lượng trong giỏ không được tràn int thành số âm
    @Test
    void cartQuantityCannotOverflow() throws Exception {
        String token = newCustomer();
        long productId = createProduct("overflow", 5, null);
        assertThat(call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1)).status())
                .isEqualTo(201);

        Res huge = call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", Integer.MAX_VALUE));
        assertThat(huge.status()).isEqualTo(400);

        Res overLimit = call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 99));
        assertThat(overLimit.status()).as("1 + 99 vượt giới hạn 99 mỗi món").isEqualTo(400);

        JsonNode item = call("GET", "/api/cart", token, null).data().path("items").path(0);
        assertThat(item.path("quantity").asInt()).isEqualTo(1);
    }

    // 2. hai lần đặt hàng cùng lúc trên cùng giỏ: đúng 1 đơn, request kia nhận lỗi 4xx rõ ràng (không phải 500)
    @Test
    void concurrentCheckoutOfSameCartCreatesOneOrder() throws Exception {
        for (int round = 0; round < 5; round++) {
            String token = newCustomer();
            long productId = createProduct("double-checkout", 10, null);
            assertThat(call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1)).status())
                    .isEqualTo(201);

            List<Res> results = runConcurrently(2, () -> call("POST", "/api/orders", token, ORDER_BODY));

            assertThat(results).extracting(Res::status).containsExactlyInAnyOrder(201, 400);
            assertThat(call("GET", "/api/orders", token, null).data().size()).isEqualTo(1);
            assertThat(stockOf(productId)).isEqualTo(9);
        }
    }

    // 3a. load khách hàng (lọc JWT, danh sách admin) không kéo thêm query giỏ hàng
    @Test
    void loadingCustomersDoesNotQueryCarts() throws Exception {
        String token = newCustomer();
        newCustomer();
        newCustomer();

        SqlRecorder.clear();
        assertThat(call("GET", "/api/me", token, null).status()).isEqualTo(200);
        assertThat(SqlRecorder.selectsFrom("carts")).isZero();

        SqlRecorder.clear();
        assertThat(call("GET", "/api/admin/users?size=100", adminToken, null).status()).isEqualTo(200);
        assertThat(SqlRecorder.selectsFrom("carts")).isZero();
    }

    // 3b. danh sách đơn không kéo thêm query payment cho từng đơn
    @Test
    void listingOrdersDoesNotQueryPayments() throws Exception {
        String token = newCustomer();
        long productId = createProduct("n-plus-one", 100, null);
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
            assertThat(call("POST", "/api/orders", token, ORDER_BODY).status()).isEqualTo(201);
        }

        SqlRecorder.clear();
        assertThat(call("GET", "/api/orders", token, null).data().size()).isEqualTo(3);
        assertThat(SqlRecorder.selectsFrom("payments")).isZero();

        SqlRecorder.clear();
        assertThat(call("GET", "/api/admin/orders?size=100", adminToken, null).status()).isEqualTo(200);
        assertThat(SqlRecorder.selectsFrom("payments")).isZero();
    }

    // 4. không ứng viên nào tác động vào nhóm yếu nhất -> vẫn gợi ý theo tổng chỉ số tác động, không trả rỗng
    @Test
    void recommendationFallsBackWhenNoCandidateHitsWeakestSkill() throws Exception {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        long logicId = createSkill("REG_LOGIC_" + suffix);
        createSkill("REG_CREATIVE_" + suffix);
        String token = newCustomer();
        long owned = createProduct("owned-logic", 10, Map.of(logicId, 5));
        long candidate = createProduct("candidate-logic", 10, Map.of(logicId, 8));

        long childId = call("POST", "/api/children", token, Map.of("name", "Reg kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        assertThat(call("POST", "/api/children/" + childId + "/products", token, Map.of("productId", owned)).status())
                .isEqualTo(201);

        Res rec = call("GET", "/api/children/" + childId + "/skill-profile/recommendations?limit=20", token, null);
        assertThat(rec.status()).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        rec.data().forEach(r -> ids.add(r.path("productId").asLong()));
        assertThat(ids).contains(candidate);
        assertThat(rec.data().path(0).path("reasons").path(0).asString()).startsWith("Chưa có sản phẩm phù hợp");
    }

    // 5. phương thức chưa hỗ trợ (VNPAY) bị từ chối và không trừ kho;
    //    MOMO tạo đơn như COD, việc thanh toán làm sau qua /api/payments
    @Test
    void unsupportedPaymentMethodIsRejectedAndMomoOrderIsCreated() throws Exception {
        String token = newCustomer();
        long productId = createProduct("vnpay", 10, null);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));

        Map<String, Object> vnpay = new HashMap<>(ORDER_BODY);
        vnpay.put("paymentMethod", "VNPAY");
        assertThat(call("POST", "/api/orders", token, vnpay).status()).isEqualTo(400);
        assertThat(stockOf(productId)).isEqualTo(10);

        Map<String, Object> momo = new HashMap<>(ORDER_BODY);
        momo.put("paymentMethod", "MOMO");
        Res order = call("POST", "/api/orders", token, momo);
        assertThat(order.status()).isEqualTo(201);
        assertThat(order.data().path("paymentMethod").asString()).isEqualTo("MOMO");
        assertThat(stockOf(productId)).isEqualTo(9);
    }

    // 6a + 7. dòng đơn trả về bé được mua cho; đơn 2 sản phẩm cùng 1 bé chỉ tính lại hồ sơ kỹ năng 1 lần
    @Test
    void orderItemShowsChildAndDeliveryRecalculatesOncePerChild() throws Exception {
        String token = newCustomer();
        long p1 = createProduct("recalc-1", 10, null);
        long p2 = createProduct("recalc-2", 10, null);
        long childId = call("POST", "/api/children", token, Map.of("name", "Recalc kid", "birthDate", "2019-01-01"))
                .data().path("id").asLong();
        call("POST", "/api/cart/items", token, Map.of("productId", p1, "quantity", 1));
        call("POST", "/api/cart/items", token, Map.of("productId", p2, "quantity", 1));
        Map<String, Object> body = new HashMap<>(ORDER_BODY);
        body.put("childAssignments", List.of(
                Map.of("productId", p1, "childProfileId", childId),
                Map.of("productId", p2, "childProfileId", childId)));
        Res order = call("POST", "/api/orders", token, body);
        assertThat(order.status()).isEqualTo(201);
        long orderId = order.data().path("id").asLong();

        JsonNode firstItem = call("GET", "/api/orders/" + orderId, token, null).data().path("items").path(0);
        assertThat(firstItem.path("childProfileId").asLong()).isEqualTo(childId);
        assertThat(firstItem.path("childName").asString()).isEqualTo("Recalc kid");

        for (String status : List.of("CONFIRMED", "SHIPPING")) {
            assertThat(call("PATCH", "/api/admin/orders/" + orderId + "/status", adminToken, Map.of("status", status)).status())
                    .isEqualTo(200);
        }
        SqlRecorder.clear();
        assertThat(call("PATCH", "/api/admin/orders/" + orderId + "/status", adminToken, Map.of("status", "DELIVERED")).status())
                .isEqualTo(200);
        // mỗi lần recalculateFor chạy đúng 1 câu đếm child_products của bé
        long recalculations = SqlRecorder.statements().stream()
                .filter(sql -> sql.startsWith("select count(") && sql.contains("from child_products")).count();
        assertThat(recalculations).isEqualTo(1);
        assertThat(call("GET", "/api/children/" + childId + "/products", token, null).data().size()).isEqualTo(2);
    }

    // 6b. dòng giỏ có tồn kho và trạng thái sản phẩm
    @Test
    void cartItemShowsStockAndActive() throws Exception {
        String token = newCustomer();
        long productId = createProduct("cart-fields", 7, null);
        JsonNode item = call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1))
                .data().path("items").path(0);
        assertThat(item.path("stockQuantity").asInt()).isEqualTo(7);
        assertThat(item.path("active").asBoolean()).isTrue();
        assertThat(item.has("thumbnailUrl")).isTrue();
    }

    // 8. đổi mật khẩu thì token cũ hết hiệu lực ngay, token mới dùng được
    @Test
    void oldTokenIsRejectedAfterPasswordChange() throws Exception {
        String email = newEmail();
        register(email);
        String oldToken = login(email, PASSWORD);
        assertThat(call("PUT", "/api/me/password", oldToken,
                Map.of("currentPassword", PASSWORD, "newPassword", "N3wPassw0rd!")).status()).isEqualTo(200);

        assertThat(call("GET", "/api/me", oldToken, null).status()).isEqualTo(401);
        String newToken = login(email, "N3wPassw0rd!");
        assertThat(call("GET", "/api/me", newToken, null).status()).isEqualTo(200);
    }

    // 9. cột role luôn khớp lớp con, kể cả khi code set nhầm
    @Test
    void roleColumnAlwaysMatchesSubclass() {
        String email = newEmail();
        Long id = inTx(() -> {
            Customer customer = Customer.builder().fullName("Wrong role").email(email).password("x")
                    .role(Role.ADMIN).build();
            em.persist(customer);
            return customer.getId();
        });
        String stored = inTx(() -> (String) em.createNativeQuery("select role from users where id = :id")
                .setParameter("id", id).getSingleResult());
        assertThat(stored).isEqualTo("CUSTOMER");
    }

    // =================================== helpers =======================================

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

    interface Call {
        Res run() throws Exception;
    }

    // chạy n request cùng lúc: các thread chờ chung một tín hiệu rồi mới gửi
    private List<Res> runConcurrently(int n, Call call) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Res> results = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    results.add(call.run());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(t);
            t.start();
        }
        ready.await();
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        return results;
    }

    private String newEmail() {
        return "reg" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
    }

    private void register(String email) throws Exception {
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Regression User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
    }

    private String login(String email, String password) throws Exception {
        Res r = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(r.status()).as("login " + email).isEqualTo(200);
        return r.data().path("token").asString();
    }

    private String newCustomer() throws Exception {
        String email = newEmail();
        register(email);
        return login(email, PASSWORD);
    }

    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private long stockOf(long productId) {
        return inTx(() -> em.createQuery("select p.stockQuantity from Product p where p.id = :id", Integer.class)
                .setParameter("id", productId).getSingleResult());
    }

    private long createSkill(String code) {
        return inTx(() -> {
            Skill skill = Skill.builder().code(code).name(code).build();
            em.persist(skill);
            return skill.getId();
        });
    }

    private long createProduct(String name, int stock, Map<Long, Integer> impacts) {
        return inTx(() -> {
            Category category = Category.builder().name("reg-" + name + "-" + System.nanoTime()).build();
            em.persist(category);
            Product product = Product.builder()
                    .name("[REG] " + name).price(new BigDecimal("100000.00"))
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
