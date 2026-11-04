package com.brainblocks.backend.fulltest.ops;

import com.brainblocks.backend.config.*;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.*;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.ai.ProductEmbeddingService;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.service.stock.LowStockPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.test.util.ReflectionTestUtils;
import com.brainblocks.backend.service.payment.MoMoGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/** Real JWT/security/transactions on H2. Scenarios already in regression tests are omitted. */
@SpringBootTest(properties = {"app.seed.demo-data=false", "app.skill-score.alpha=0.5"})
@ActiveProfiles("test")
class OperationsApiTest {
    private static final JsonMapper JSON = new JsonMapper();
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired SkillRepository skills;
    @Autowired PaymentRepository payments;
    @Autowired OrderRepository orders;
    @Autowired LowStockPolicy lowStockPolicy;
    @Autowired UnpaidOrderExpiryJob unpaidJob;
    @Autowired SkillProfileRecalculationRunner profileRunner;
    @Autowired CatalogSeeder catalogSeeder;
    @Autowired AdminSeeder adminSeeder;
    @Autowired ChatbotConfigSeeder chatbotSeeder;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    @MockitoBean ProductEmbeddingService embeddings;
    @MockitoBean com.brainblocks.backend.service.payment.MoMoGateway gateway;
    MockMvc mvc;
    String admin;

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }
    record Account(String token, long id) {}

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        admin = login("admin@test.local", "test-admin-password");
    }

    @Test
    void defaultLowStockThresholdIsFive() {
        assertThat(lowStockPolicy.threshold()).isEqualTo(5);
    }

    @Test
    void checkoutWarnsOncePerCrossingAndRestockingAllowsAnotherWarning() throws Exception {
        Account buyer = customer();
        Product p = product("ops-crossing-" + UUID.randomUUID(), 8, true, null);
        long before = lowStockNotifications(p.getName());
        checkout(buyer.token(), p.getId(), 3, "COD", Map.of());
        assertThat(stock(p.getId())).isEqualTo(5);
        assertThat(lowStockNotifications(p.getName())).isEqualTo(before + 1);
        checkout(buyer.token(), p.getId(), 1, "COD", Map.of());
        assertThat(lowStockNotifications(p.getName())).isEqualTo(before + 1);
        ok("PATCH", "/api/admin/products/" + p.getId() + "/stock", admin, Map.of("stockQuantity", 8), 200);
        checkout(buyer.token(), p.getId(), 4, "COD", Map.of());
        assertThat(lowStockNotifications(p.getName())).isEqualTo(before + 2);
    }

    @Test
    void lowStockEndpointIncludesZeroAndThresholdExcludesHiddenAndSortsByStockThenId() throws Exception {
        Product atThreshold = product("threshold", 5, true, null);
        Product zero = product("zero", 0, true, null);
        Product tie = product("tie", 0, true, null);
        Product hidden = product("hidden", 0, false, null);
        Product high = product("high", 6, true, null);
        List<Long> expected = jdbc.queryForList("select id from products where active=true and stock_quantity<=5 "
                + "order by stock_quantity,id limit 100", Long.class);
        JsonNode result = ok("GET", "/api/admin/products/low-stock?limit=100", admin, null, 200);
        assertThat(ids(result)).containsExactlyElementsOf(expected);
        assertThat(ids(result)).contains(zero.getId(), tie.getId(), atThreshold.getId())
                .doesNotContain(hidden.getId(), high.getId());
        assertThat(result.valueStream().filter(n -> n.path("id").asLong() == atThreshold.getId())
                .findFirst().orElseThrow().path("stockQuantity").asInt()).isEqualTo(5);
    }

    @Test
    void lowStockLimitClampsZeroNegativeAndHugeValues() throws Exception {
        List<Product> fixtures = new ArrayList<>();
        Category category = categories.save(Category.builder().name("Ops limits " + UUID.randomUUID()).build());
        for (int i = 0; i < 101; i++) fixtures.add(newProduct("limit " + i, 0, true, category));
        products.saveAll(fixtures);
        try {
            List<Long> expected = jdbc.queryForList("select id from products where active=true and stock_quantity<=5 "
                    + "order by stock_quantity,id limit 100", Long.class);
            for (int limit : new int[]{0, -1, Integer.MIN_VALUE}) {
                assertThat(ids(ok("GET", "/api/admin/products/low-stock?limit=" + limit, admin, null, 200)))
                        .containsExactly(expected.getFirst());
            }
            assertThat(ids(ok("GET", "/api/admin/products/low-stock?limit=" + Integer.MAX_VALUE, admin, null, 200)))
                    .hasSize(100).containsExactlyElementsOf(expected);
        } finally {
            // Keep the large fixture from filling subsequent tests' low-stock lists.
            for (Product p : fixtures) jdbc.update("update products set active=false where id=?", p.getId());
        }
    }

    @Test
    void customerCannotReadAdminLowStockList() throws Exception {
        assertThat(call("GET", "/api/admin/products/low-stock", customer().token(), null).status()).isEqualTo(403);
    }

    @Test
    void notificationsPageUsesIdTieBreakerAndDoesNotLeakOtherRecipients() throws Exception {
        Account owner = customer();
        Account other = customer();
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) created.add(notification(owner.id(), "page " + i));
        notification(other.id(), "foreign");
        // Explicitly equal timestamps exercise the secondary ordering by descending ID.
        jdbc.update("update notifications set created_at=? where recipient_id=?", LocalDateTime.of(2020, 1, 1, 0, 0), owner.id());
        Collections.reverse(created);
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode data = ok("GET", "/api/notifications?page=" + page + "&size=2", owner.token(), null, 200);
            assertThat(data.path("page").asInt()).isEqualTo(page);
            assertThat(data.path("totalElements").asInt()).isEqualTo(5);
            assertThat(data.path("totalPages").asInt()).isEqualTo(3);
            seen.addAll(ids(data.path("content")));
        }
        assertThat(seen).containsExactlyElementsOf(created);
        assertThat(ok("GET", "/api/notifications?page=3&size=2", owner.token(), null, 200).path("content").size()).isZero();
    }

    @Test
    void notificationPageSizeIsCappedAtFifty() throws Exception {
        Account owner = customer();
        for (int i = 0; i < 51; i++) notification(owner.id(), "cap " + i);
        JsonNode first = ok("GET", "/api/notifications?size=2147483647", owner.token(), null, 200);
        assertThat(first.path("size").asInt()).isEqualTo(50);
        assertThat(first.path("content").size()).isEqualTo(50);
        assertThat(first.path("totalElements").asInt()).isEqualTo(51);
        JsonNode second = ok("GET", "/api/notifications?page=1&size=50", owner.token(), null, 200);
        assertThat(second.path("content").size()).isEqualTo(1);
        assertThat(ids(first.path("content"))).doesNotContainAnyElementsOf(ids(second.path("content")));
    }

    @Test
    void unpaidJobReleasesVoucherAndSecondRunDoesNotRestoreStockOrCartAgain() throws Exception {
        Account buyer = customer();
        Product p = product("expiry voucher", 10, true, null);
        JsonNode voucher = ok("GET", "/api/vouchers", buyer.token(), null, 200).valueStream()
                .filter(n -> n.path("type").asString().equals("PERCENT_OFF")).findFirst().orElseThrow();
        long voucherId = voucher.path("id").asLong();
        long order = checkout(buyer.token(), p.getId(), 2, "MOMO", Map.of("discountVoucherId", voucherId)).path("id").asLong();
        assertThat(voucherStatus(buyer.token(), voucherId)).isEqualTo("USED");
        ageOrder(order);
        unpaidJob.cancelExpiredUnpaidOrders();
        long count = jdbc.queryForObject("select count(*) from notifications where recipient_id=?", Long.class, buyer.id());
        unpaidJob.cancelExpiredUnpaidOrders();
        assertThat(ok("GET", "/api/orders/" + order, buyer.token(), null, 200).path("status").asString()).isEqualTo("CANCELLED");
        assertThat(stock(p.getId())).isEqualTo(10);
        assertThat(voucherStatus(buyer.token(), voucherId)).isEqualTo("AVAILABLE");
        JsonNode cart = ok("GET", "/api/cart", buyer.token(), null, 200).path("items");
        assertThat(cart.size()).isEqualTo(1);
        assertThat(cart.path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_id=?", Long.class, buyer.id())).isEqualTo(count);
    }

    @Test
    void unpaidJobLeavesOldCodAndSuccessfullyPaidMomoOrdersUntouched() throws Exception {
        Account buyer = customer();
        Product p = product("expiry exclusions", 10, true, null);
        long cod = checkout(buyer.token(), p.getId(), 1, "COD", Map.of()).path("id").asLong();
        long paid = checkout(buyer.token(), p.getId(), 1, "MOMO", Map.of()).path("id").asLong();
        ageOrder(cod);
        ageOrder(paid);
        // Persist the same payment/order state established by a successful IPN, without contacting MoMo.
        jdbc.update("update orders set status='CONFIRMED' where id=?", paid);
        Order paidOrder = orders.findById(paid).orElseThrow();
        payments.save(Payment.builder().order(paidOrder)
                .status(PaymentStatus.SUCCESS).amount(paidOrder.getTotalAmount())
                .paidAt(LocalDateTime.now().minusHours(25)).build());
        unpaidJob.cancelExpiredUnpaidOrders();
        unpaidJob.cancelExpiredUnpaidOrders();
        assertThat(ok("GET", "/api/orders/" + cod, buyer.token(), null, 200).path("status").asString()).isEqualTo("PENDING");
        assertThat(ok("GET", "/api/orders/" + paid, buyer.token(), null, 200).path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(payments.findByOrderId(paid).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stock(p.getId())).isEqualTo(8);
        assertThat(ok("GET", "/api/cart", buyer.token(), null, 200).path("items").size()).isZero();
    }

    @Test
    void validSignedMomoReturnIsPublicAndOnlyDisplaysResult() throws Exception {
        localSignatureVerification();
        Map<String, String> params = signedReturn("0");
        var request = MockMvcRequestBuilders.get("/api/payments/momo-return");
        params.forEach(request::param);
        long orderCount = orders.count();
        long paymentCount = payments.count();
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("message").asString()).isEqualTo("Payment successful");
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(payments.count()).isEqualTo(paymentCount);
        verify(gateway, never()).createPaymentUrl(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(gateway, never()).extractTxnRef(anyMap());
    }

    @Test
    void validSignatureWithFailedMomoResultReturnsFailure() throws Exception {
        localSignatureVerification();
        assertReturnFailure(signedReturn("1006"));
    }

    @Test
    void tamperedMomoReturnReturnsFailureInsteadOfServerError() throws Exception {
        localSignatureVerification();
        Map<String, String> params = signedReturn("0");
        params.put("amount", "1");
        assertReturnFailure(params);
    }

    @Test
    void missingAndMalformedMomoReturnParametersDoNotCauseServerError() throws Exception {
        localSignatureVerification();
        assertReturnFailure(Map.of());
        assertReturnFailure(Map.of("signature", "not-hex", "resultCode", "garbage", "orderId", "not-a-number"));
    }

    @Test
    void ownerRecalculateRepairsStaleScoresFromAssignedProducts() throws Exception {
        Account owner = customer();
        long child = child(owner.token());
        Product p = product("recalculate", 10, true, 8);
        ok("POST", "/api/children/" + child + "/products", owner.token(), Map.of("productId", p.getId()), 201);
        corruptScores(child);
        JsonNode response = ok("POST", "/api/children/" + child + "/skill-profile/recalculate", owner.token(), null, 200);
        assertProfile(response, child, 1, 4.0);
    }

    @Test
    void otherParentCannotRecalculateOrModifyForeignScores() throws Exception {
        Account owner = customer();
        long child = child(owner.token());
        corruptScores(child);
        List<BigDecimal> before = storedScores(child);
        assertThat(call("POST", "/api/children/" + child + "/skill-profile/recalculate", customer().token(), null).status()).isEqualTo(404);
        assertThat(storedScores(child)).containsExactlyElementsOf(before);
    }

    @Test
    void anonymousCannotRecalculateSkillProfile() throws Exception {
        long child = child(customer().token());
        assertThat(call("POST", "/api/children/" + child + "/skill-profile/recalculate", null, null).status()).isIn(401, 403);
    }

    @Test
    void startupRunnerRepairsBothAssignedAndEmptyProfilesAndIsRepeatable() throws Exception {
        Account owner = customer();
        long assigned = child(owner.token());
        long empty = child(owner.token());
        Product p = product("runner", 10, true, 8);
        ok("POST", "/api/children/" + assigned + "/products", owner.token(), Map.of("productId", p.getId()), 201);
        corruptScores(assigned);
        corruptScores(empty);
        profileRunner.run();
        profileRunner.run();
        assertProfile(ok("GET", "/api/children/" + assigned + "/skill-profile", owner.token(), null, 200), assigned, 1, 4.0);
        assertProfile(ok("GET", "/api/children/" + empty + "/skill-profile", owner.token(), null, 200), empty, 0, 0.0);
        assertThat(jdbc.queryForObject("select count(*) from skill_profiles where child_profile_id in (?,?)", Long.class, assigned, empty)).isEqualTo(2);
    }

    @Test
    void rerunningCatalogSeederPreservesSkillCategoryAndProductIds() {
        List<Long> skillIds = tableIds("skills");
        List<Long> categoryIds = tableIds("categories");
        List<Long> productIds = tableIds("products");
        catalogSeeder.run();
        catalogSeeder.run();
        assertThat(tableIds("skills")).containsExactlyElementsOf(skillIds);
        assertThat(tableIds("categories")).containsExactlyElementsOf(categoryIds);
        assertThat(tableIds("products")).containsExactlyElementsOf(productIds);
        for (String code : List.of("LOGIC", "CREATIVE", "PROBLEM_SOLVING", "STEM")) {
            assertThat(jdbc.queryForObject("select count(*) from skills where code=?", Long.class, code)).isEqualTo(1);
        }
    }

    @Test
    void rerunningAdminSeederPreservesExistingIdentityAndPassword() {
        List<Long> before = tableIds("users");
        String hash = jdbc.queryForObject("select password from users where email='admin@test.local'", String.class);
        adminSeeder.run();
        adminSeeder.run();
        assertThat(tableIds("users")).containsExactlyElementsOf(before);
        assertThat(jdbc.queryForObject("select password from users where email='admin@test.local'", String.class)).isEqualTo(hash);
    }

    @Test
    void rerunningChatbotSeederPreservesConfiguration() {
        List<Long> before = tableIds("chatbot_configs");
        assertThat(before).isNotEmpty();
        List<Map<String, Object>> settings = jdbc.queryForList("select * from chatbot_configs order by id");
        chatbotSeeder.seedDefaultConfig();
        chatbotSeeder.seedDefaultConfig();
        assertThat(tableIds("chatbot_configs")).containsExactlyElementsOf(before);
        assertThat(jdbc.queryForList("select * from chatbot_configs order by id")).isEqualTo(settings);
    }

    private List<Long> tableIds(String table) {
        return jdbc.queryForList("select id from " + table + " order by id", Long.class);
    }

    private void assertProfile(JsonNode data, long child, int total, double logic) {
        assertThat(data.path("childProfileId").asLong()).isEqualTo(child);
        assertThat(data.path("totalProducts").asInt()).isEqualTo(total);
        assertThat(data.path("skillScores").size()).isEqualTo((int) skills.count());
        for (JsonNode score : data.path("skillScores")) {
            assertThat(score.path("score").asDouble()).isEqualTo(score.path("skillCode").asString().equals("LOGIC") ? logic : 0.0);
        }
    }

    private void corruptScores(long child) {
        jdbc.update("update skill_scores set score=9 where skill_profile_id in (select id from skill_profiles where child_profile_id=?)", child);
        assertThat(storedScores(child)).isNotEmpty().allMatch(value -> value.doubleValue() == 9.0);
    }

    private List<BigDecimal> storedScores(long child) {
        return jdbc.queryForList("select score from skill_scores where skill_profile_id in "
                + "(select id from skill_profiles where child_profile_id=?) order by id", BigDecimal.class, child);
    }

    private long child(String token) throws Exception {
        return ok("POST", "/api/children", token, Map.of("name", "Ops child", "birthDate", "2019-01-01"), 201).path("id").asLong();
    }

    private long notification(long owner, String title) {
        jdbc.update("insert into notifications(recipient_id,type,title,is_read,created_at) values (?,'ORDER_STATUS',?,false,?)", owner, title, LocalDateTime.now());
        return jdbc.queryForObject("select max(id) from notifications where recipient_id=?", Long.class, owner);
    }

    private long lowStockNotifications(String name) {
        long adminId = jdbc.queryForObject("select id from users where email='admin@test.local'", Long.class);
        return jdbc.queryForObject("select count(*) from notifications where recipient_id=? and type='LOW_STOCK' and title like ?", Long.class, adminId, "%" + name);
    }

    private void ageOrder(long id) {
        jdbc.update("update orders set created_at=? where id=?", LocalDateTime.now().minusHours(25), id);
    }

    private int stock(long id) {
        return jdbc.queryForObject("select stock_quantity from products where id=?", Integer.class, id);
    }

    private String voucherStatus(String token, long id) throws Exception {
        return ok("GET", "/api/vouchers", token, null, 200).valueStream().filter(n -> n.path("id").asLong() == id)
                .findFirst().orElseThrow().path("status").asString();
    }

    private JsonNode checkout(String token, long product, int quantity, String method, Map<String, Object> extras) throws Exception {
        ok("POST", "/api/cart/items", token, Map.of("productId", product, "quantity", quantity), 201);
        Map<String, Object> body = new HashMap<>(Map.of("receiverName", "Ops buyer", "receiverPhone", "0901234567",
                "provinceCode", 92, "districtCode", 916, "wardCode", 31117, "addressDetail", "1 Test", "paymentMethod", method));
        body.putAll(extras);
        return ok("POST", "/api/orders", token, body, 201);
    }

    private Product product(String name, int stock, boolean active, Integer impact) {
        Category category = categories.save(Category.builder().name("Ops " + UUID.randomUUID()).build());
        Product p = newProduct(name, stock, active, category);
        if (impact != null) p.getProductSkillImpacts().add(ProductSkillImpact.builder().product(p)
                .skill(skills.findByCode("LOGIC").orElseThrow()).impactIndex(impact).build());
        return products.save(p);
    }

    private Product newProduct(String name, int stock, boolean active, Category category) {
        return Product.builder().name(name).category(category).price(new BigDecimal("100000"))
                .stockQuantity(stock).active(active).minAge(0).maxAge(18)
                .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
    }

    private Account customer() throws Exception {
        String email = "ops-" + UUID.randomUUID() + "@test.local";
        long id = ok("POST", "/api/auth/register", null, Map.of("fullName", "Ops parent", "email", email, "password", "Passw0rd!"), 201).path("id").asLong();
        return new Account(login(email, "Passw0rd!"), id);
    }

    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password), 200).path("token").asString();
    }

    private void localSignatureVerification() {
        MoMoGateway local = new MoMoGateway();
        ReflectionTestUtils.setField(local, "accessKey", "ops-access");
        ReflectionTestUtils.setField(local, "secretKey", "ops-secret");
        // Exercise real signature/result parsing while the bean's network method remains mocked.
        when(gateway.verifySignature(anyMap())).thenAnswer(invocation -> local.verifySignature(invocation.getArgument(0)));
        when(gateway.isSuccess(anyMap())).thenAnswer(invocation -> local.isSuccess(invocation.getArgument(0)));
    }

    private Map<String, String> signedReturn(String resultCode) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", "100000");
        params.put("extraData", "");
        params.put("message", "done");
        params.put("orderId", "999999999");
        params.put("orderInfo", "Ops payment");
        params.put("orderType", "momo_wallet");
        params.put("partnerCode", "OPS");
        params.put("payType", "qr");
        params.put("requestId", "ops-request");
        params.put("responseTime", "1700000000000");
        params.put("resultCode", resultCode);
        params.put("transId", "ops-trans");
        String raw = "accessKey=ops-access";
        for (var entry : params.entrySet()) raw += "&" + entry.getKey() + "=" + entry.getValue();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("ops-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        params.put("signature", HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8))));
        return params;
    }

    private void assertReturnFailure(Map<String, String> params) throws Exception {
        var request = MockMvcRequestBuilders.get("/api/payments/momo-return");
        params.forEach(request::param);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asString()).isEqualTo("Payment failed or invalid signature");
    }

    private List<Long> ids(JsonNode array) {
        return array.valueStream().map(n -> n.path("id").asLong()).toList();
    }

    private JsonNode ok(String method, String path, String token, Object body, int status) throws Exception {
        Response response = call(method, path, token, body);
        assertThat(response.status()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        return response.data();
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(JSON.writeValueAsBytes(body));
        var result = mvc.perform(request).andReturn().getResponse();
        String raw = result.getContentAsString(StandardCharsets.UTF_8);
        return new Response(result.getStatus(), raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw));
    }
}
