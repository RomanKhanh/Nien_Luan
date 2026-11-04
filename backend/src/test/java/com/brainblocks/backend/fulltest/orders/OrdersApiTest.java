package com.brainblocks.backend.fulltest.orders;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.ai.ProductEmbeddingService;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.service.payment.MoMoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real JWT, security filters, controllers and committed transactions on the test profile's H2.
 * Unique fixtures avoid depending on execution order or seeded orders. No surrounding test
 * transaction: checkout failures must really roll back, and competing requests must see commits.
 * All external mail/AI/payment integrations are replaced before the context starts.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrdersApiTest {
    private static final JsonMapper JSON = new JsonMapper();
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    @MockitoBean ProductEmbeddingService productEmbeddingService;
    @MockitoBean MoMoGateway gateway;
    private MockMvc mvc;
    private String admin;
    private String buyer;
    private long product;

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        admin = login("admin@test.local", "test-admin-password");
        buyer = customer();
        long category = ok("POST", "/api/admin/categories", admin,
                Map.of("name", "Orders " + UUID.randomUUID()), 201).path("id").asLong();
        long skill = ok("GET", "/api/skills", null, null, 200).path(0).path("id").asLong();
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Orders blocks " + UUID.randomUUID());
        body.put("price", 100000);
        body.put("stockQuantity", 10);
        body.put("minAge", 0);
        body.put("maxAge", 18);
        body.put("categoryId", category);
        body.put("weightGrams", 500);
        body.put("lengthCm", 20);
        body.put("widthCm", 15);
        body.put("heightCm", 10);
        body.put("skillImpacts", List.of(Map.of("skillId", skill, "impactIndex", 8)));
        product = ok("POST", "/api/admin/products", admin, body, 201).path("id").asLong();
    }

    @Test
    void cartAddsMergesUpdatesRemovesAndClearsWithCorrectTotals() throws Exception {
        JsonNode first = add(buyer, 2);
        long item = first.path("items").path(0).path("id").asLong();
        assertThat(item).isPositive();
        assertThat(first.path("totalAmount").asInt()).isEqualTo(200000);
        JsonNode merged = add(buyer, 3);
        assertThat(merged.path("items").size()).isEqualTo(1);
        assertThat(merged.path("items").path(0).path("id").asLong()).isEqualTo(item);
        assertThat(merged.path("items").path(0).path("quantity").asInt()).isEqualTo(5);
        assertThat(merged.path("totalAmount").asInt()).isEqualTo(500000);
        JsonNode changed = ok("PATCH", "/api/cart/items/" + item, buyer, Map.of("quantity", 4), 200);
        assertThat(changed.path("totalAmount").asInt()).isEqualTo(400000);
        JsonNode removed = ok("DELETE", "/api/cart/items/" + item, buyer, null, 200);
        assertThat(removed.path("items").size()).isZero();
        assertThat(removed.path("totalAmount").asInt()).isZero();
        add(buyer, 1);
        ok("DELETE", "/api/cart", buyer, null, 200);
        assertThat(cart(buyer).path("items").size()).isZero();
        assertThat(cart(buyer).path("totalAmount").asInt()).isZero();
        assertThat(stock()).isEqualTo(10); // cart edits do not reserve inventory
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, Integer.MAX_VALUE})
    void invalidCartAddAndUpdateLeaveQuantityUnchanged(int quantity) throws Exception {
        long item = add(buyer, 1).path("items").path(0).path("id").asLong();
        expect(call("POST", "/api/cart/items", buyer, Map.of("productId", product, "quantity", quantity)), 400);
        expect(call("PATCH", "/api/cart/items/" + item, buyer, Map.of("quantity", quantity)), 400);
        assertThat(cart(buyer).path("items").path(0).path("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void mergedCartQuantityCannotExceedStock() throws Exception {
        add(buyer, 6);
        expect(call("POST", "/api/cart/items", buyer, Map.of("productId", product, "quantity", 5)), 400);
        assertThat(cart(buyer).path("totalAmount").asInt()).isEqualTo(600000);
    }

    @Test
    void hiddenAndMissingProductsCannotBeAdded() throws Exception {
        ok("DELETE", "/api/admin/products/" + product, admin, null, 200);
        for (long id : new long[]{product, Long.MAX_VALUE}) {
            expect(call("POST", "/api/cart/items", buyer, Map.of("productId", id, "quantity", 1)), 404);
        }
        assertThat(cart(buyer).path("items").size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    void anotherCustomersCartItemIsNotAccessible(String method) throws Exception {
        long item = add(buyer, 2).path("items").path(0).path("id").asLong();
        String other = customer();
        expect(call(method, "/api/cart/items/" + item, other,
                method.equals("PATCH") ? Map.of("quantity", 3) : null), 404);
        assertThat(cart(buyer).path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(cart(other).path("items").size()).isZero();
    }

    @Test
    void codCheckoutUsesServerTotalsConsumesStockAndClearsCart() throws Exception {
        add(buyer, 2);
        JsonNode order = checkout(buyer, orderBody());
        assertThat(order.path("status").asString()).isEqualTo("PENDING");
        assertThat(order.path("paymentMethod").asString()).isEqualTo("COD");
        assertThat(order.path("subtotal").asInt()).isEqualTo(200000);
        assertThat(order.path("shippingFee").asInt()).isPositive();
        assertThat(order.path("totalAmount").asInt()).isEqualTo(200000 + order.path("shippingFee").asInt());
        assertThat(order.path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(order.path("items").path(0).path("unitPrice").asInt()).isEqualTo(100000);
        assertThat(stock()).isEqualTo(8);
        assertThat(cart(buyer).path("items").size()).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void vouchersReduceTotalAndPendingCancellationRestoresStockAndVouchersOnlyOnce() throws Exception {
        JsonNode vouchers = ok("GET", "/api/vouchers", buyer, null, 200);
        long shipping = rowByText(vouchers, "type", "FREESHIP").path("id").asLong();
        long discount = rowByText(vouchers, "type", "PERCENT_OFF").path("id").asLong();
        add(buyer, 3);
        var body = orderBody();
        body.put("shippingVoucherId", shipping);
        body.put("discountVoucherId", discount);
        JsonNode order = checkout(buyer, body);
        assertThat(order.path("subtotal").asInt()).isEqualTo(300000);
        assertThat(order.path("shippingDiscount").asInt()).isEqualTo(order.path("shippingFee").asInt());
        assertThat(order.path("discountAmount").asInt()).isEqualTo(30000);
        assertThat(order.path("totalAmount").asInt()).isEqualTo(270000);
        assertThat(stock()).isEqualTo(7);
        JsonNode used = ok("GET", "/api/vouchers", buyer, null, 200);
        for (long id : new long[]{shipping, discount}) {
            assertThat(rowById(used, id).path("status").asString()).isEqualTo("USED");
        }
        long id = order.path("id").asLong();
        JsonNode cancelled = ok("PATCH", "/api/orders/" + id + "/cancel", buyer, null, 200);
        assertThat(cancelled.path("status").asString()).isEqualTo("CANCELLED");
        assertThat(stock()).isEqualTo(10);
        JsonNode released = ok("GET", "/api/vouchers", buyer, null, 200);
        for (long voucher : new long[]{shipping, discount}) {
            assertThat(rowById(released, voucher).path("status").asString()).isEqualTo("AVAILABLE");
        }
        expect(call("PATCH", "/api/orders/" + id + "/cancel", buyer, null), 400);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void emptyCartCannotCheckout() throws Exception {
        expect(call("POST", "/api/orders", buyer, orderBody()), 400);
        assertThat(myOrders(buyer).size()).isZero();
        assertThat(stock()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stockLostOrProductHiddenAfterAddingToCartPreventsCheckout(boolean hide) throws Exception {
        add(buyer, 2);
        if (hide) ok("DELETE", "/api/admin/products/" + product, admin, null, 200);
        else setStock(1);
        expect(call("POST", "/api/orders", buyer, orderBody()), 400);
        assertThat(myOrders(buyer).size()).isZero();
        assertThat(cart(buyer).path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(stock()).isEqualTo(hide ? 10 : 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"receiverName", "receiverPhone", "provinceCode", "districtCode", "wardCode",
            "addressDetail", "paymentMethod", "wrongPhone", "wrongProvince", "wrongDistrict", "wrongWard"})
    void missingOrInvalidDeliveryDataDoesNotConsumeStock(String invalid) throws Exception {
        add(buyer, 1);
        var body = orderBody();
        switch (invalid) {
            case "wrongPhone" -> body.put("receiverPhone", "abc");
            case "wrongProvince" -> body.put("provinceCode", 999);
            case "wrongDistrict" -> body.put("districtCode", 760); // HCMC district, Can Tho province
            case "wrongWard" -> body.put("wardCode", 31153); // a different district
            default -> body.remove(invalid);
        }
        expect(call("POST", "/api/orders", buyer, body), 400);
        assertThat(stock()).isEqualTo(10);
        assertThat(cart(buyer).path("items").size()).isEqualTo(1);
        assertThat(myOrders(buyer).size()).isZero();
    }

    @Test
    void changedShippingQuoteRollsBackStockAndKeepsCart() throws Exception {
        add(buyer, 2);
        var body = orderBody();
        body.put("expectedShippingFee", 0);
        expect(call("POST", "/api/orders", buyer, body), 409);
        assertThat(stock()).isEqualTo(10);
        assertThat(cart(buyer).path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(myOrders(buyer).size()).isZero();
    }

    @Test
    void assignedChildReceivesPurchasedProductAndSkillScoreOnlyAfterDelivery() throws Exception {
        long child = child(buyer);
        add(buyer, 1);
        var body = orderBody();
        body.put("childAssignments", List.of(Map.of("productId", product, "childProfileId", child)));
        JsonNode order = checkout(buyer, body);
        long id = order.path("id").asLong();
        assertThat(order.path("items").path(0).path("childProfileId").asLong()).isEqualTo(child);
        String profile = "/api/children/" + child + "/skill-profile";
        assertThat(ok("GET", profile, buyer, null, 200).path("totalProducts").asInt()).isZero();
        status(id, "CONFIRMED");
        status(id, "SHIPPING");
        assertThat(ok("GET", profile, buyer, null, 200).path("totalProducts").asInt()).isZero();
        status(id, "DELIVERED");
        JsonNode after = ok("GET", profile, buyer, null, 200);
        assertThat(after.path("totalProducts").asInt()).isEqualTo(1);
        assertThat(after.path("skillScores").size()).isPositive();
        assertThat(scores(after)).anyMatch(score -> score > 0);
        assertThat(stock()).isEqualTo(9);
        expect(call("PATCH", "/api/admin/orders/" + id + "/status", admin, Map.of("status", "DELIVERED")), 400);
        assertThat(ok("GET", profile, buyer, null, 200).path("totalProducts").asInt()).isEqualTo(1);
    }

    @Test
    void assigningAnotherParentsChildIsBlockedWithoutChangingCartOrStock() throws Exception {
        long child = child(customer());
        add(buyer, 1);
        var body = orderBody();
        body.put("childAssignments", List.of(Map.of("productId", product, "childProfileId", child)));
        expect(call("POST", "/api/orders", buyer, body), 404);
        assertThat(stock()).isEqualTo(10);
        assertThat(cart(buyer).path("items").size()).isEqualTo(1);
        assertThat(myOrders(buyer).size()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"PENDING,SHIPPING", "PENDING,DELIVERED", "CONFIRMED,PENDING", "CONFIRMED,DELIVERED",
            "SHIPPING,PENDING", "SHIPPING,CONFIRMED", "SHIPPING,CANCELLED",
            "DELIVERED,PENDING", "DELIVERED,CONFIRMED", "DELIVERED,SHIPPING", "DELIVERED,CANCELLED",
            "CANCELLED,PENDING", "CANCELLED,CONFIRMED", "CANCELLED,SHIPPING", "CANCELLED,DELIVERED"})
    void adminCannotSkipReverseOrChangeTerminalStatus(String from, String to) throws Exception {
        long id = place(buyer, "COD");
        advance(id, from);
        int before = stock();
        expect(call("PATCH", "/api/admin/orders/" + id + "/status", admin, Map.of("status", to)), 400);
        assertThat(detail(buyer, id).path("status").asString()).isEqualTo(from);
        assertThat(stock()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "SHIPPING", "DELIVERED"})
    void customerCannotCancelAfterPending(String state) throws Exception {
        long id = place(buyer, "COD");
        advance(id, state);
        expect(call("PATCH", "/api/orders/" + id + "/cancel", buyer, null), 400);
        assertThat(detail(buyer, id).path("status").asString()).isEqualTo(state);
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void orderHistoryDetailCancellationAndPaymentAreScopedToOwner() throws Exception {
        long mine = place(buyer, "COD");
        String other = customer();
        long theirs = place(other, "MOMO");
        assertThat(ids(myOrders(buyer))).containsExactly(mine);
        assertThat(ids(myOrders(other))).containsExactly(theirs);
        assertThat(detail(buyer, mine).path("id").asLong()).isEqualTo(mine);
        expect(call("GET", "/api/orders/" + theirs, buyer, null), 404);
        expect(call("PATCH", "/api/orders/" + theirs + "/cancel", buyer, null), 404);
        expect(call("POST", "/api/payments/" + theirs + "/create-url", buyer, null), 404);
        expect(call("GET", "/api/payments/" + theirs, buyer, null), 404);
        assertThat(detail(other, theirs).path("status").asString()).isEqualTo("PENDING");
        assertThat(stock()).isEqualTo(8);
        verifyNoInteractions(gateway);
    }

    @Test
    void adminOrderListFiltersStatus() throws Exception {
        long pending = place(buyer, "COD");
        long confirmed = place(buyer, "COD");
        status(confirmed, "CONFIRMED");
        JsonNode page = ok("GET", "/api/admin/orders?status=CONFIRMED&size=100", admin, null, 200);
        assertThat(ids(page.path("content"))).contains(confirmed).doesNotContain(pending);
        for (JsonNode order : page.path("content")) {
            assertThat(order.path("status").asString()).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void momoUrlUsesMockGatewayAndValidIpnPaysOrderIdempotently() throws Exception {
        long id = place(buyer, "MOMO");
        when(gateway.createPaymentUrl(any(Order.class), anyString(), anyString())).thenReturn("https://example.invalid/pay");
        JsonNode url = ok("POST", "/api/payments/" + id + "/create-url", buyer, null, 200);
        assertThat(url.path("paymentUrl").asString()).isEqualTo("https://example.invalid/pay");
        int total = detail(buyer, id).path("totalAmount").asInt();
        verify(gateway).createPaymentUrl(argThat(o -> o.getId().equals(id)
                && o.getTotalAmount().compareTo(BigDecimal.valueOf(total)) == 0),
                startsWith(id + "-"), anyString());
        configureIpn(id, true, String.valueOf(total));
        ok("POST", "/api/payments/momo-ipn", null, Map.of("orderId", id + "-test"), 204);
        assertThat(payment(id).path("status").asString()).isEqualTo("SUCCESS");
        assertThat(payment(id).path("transactionId").asString()).isEqualTo("orders-txn");
        assertThat(payment(id).path("paidAt").isNull()).isFalse();
        assertThat(detail(buyer, id).path("status").asString()).isEqualTo("CONFIRMED");
        ok("POST", "/api/payments/momo-ipn", null, Map.of("orderId", id + "-test"), 204);
        assertThat(stock()).isEqualTo(9);
        expect(call("POST", "/api/payments/" + id + "/create-url", buyer, null), 400);
        verify(gateway, times(1)).createPaymentUrl(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidSignatureOrWrongAmountIpnLeavesPaymentPending(boolean validSignature) throws Exception {
        long id = place(buyer, "MOMO");
        when(gateway.createPaymentUrl(any(), anyString(), anyString())).thenReturn("https://example.invalid/pay");
        ok("POST", "/api/payments/" + id + "/create-url", buyer, null, 200);
        configureIpn(id, validSignature, "1");
        // IPN always acknowledges 204; rejection means no financial/order mutation.
        ok("POST", "/api/payments/momo-ipn", null, Map.of("signature", "invalid-or-wrong-amount"), 204);
        assertThat(payment(id).path("status").asString()).isEqualTo("PENDING");
        assertThat(detail(buyer, id).path("status").asString()).isEqualTo("PENDING");
        assertThat(stock()).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COD", "CANCELLED"})
    void codAndCancelledOrdersCannotCreatePaymentUrl(String kind) throws Exception {
        long id = place(buyer, kind.equals("COD") ? "COD" : "MOMO");
        if (kind.equals("CANCELLED")) ok("PATCH", "/api/orders/" + id + "/cancel", buyer, null, 200);
        expect(call("POST", "/api/payments/" + id + "/create-url", buyer, null), 400);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "SHIPPING", "CANCELLED", "NO_PURCHASE"})
    void reviewRequiresDeliveredPurchase(String state) throws Exception {
        if (!state.equals("NO_PURCHASE")) advance(place(buyer, "COD"), state);
        expect(call("POST", reviewPath(), buyer, Map.of("rating", 5)), 400);
        assertThat(ok("GET", reviewPath(), null, null, 200).path("totalElements").asInt()).isZero();
    }

    @Test
    void deliveredPurchaseCanBeReviewedOnceAndAdminCanHideIt() throws Exception {
        advance(place(buyer, "COD"), "DELIVERED");
        long review = ok("POST", reviewPath(), buyer, Map.of("rating", 4, "comment", "Useful blocks"), 201)
                .path("id").asLong();
        expect(call("POST", reviewPath(), buyer, Map.of("rating", 5)), 400);
        assertThat(ids(ok("GET", reviewPath(), null, null, 200).path("content"))).containsExactly(review);
        JsonNode detail = ok("GET", "/api/products/" + product, null, null, 200);
        assertThat(detail.path("averageRating").asDouble()).isEqualTo(4);
        assertThat(detail.path("reviewCount").asInt()).isEqualTo(1);
        ok("PATCH", "/api/admin/reviews/" + review + "/visibility", admin, Map.of("visible", false), 200);
        assertThat(ok("GET", reviewPath(), null, null, 200).path("content").size()).isZero();
        assertThat(ok("GET", "/api/products/" + product, null, null, 200).path("reviewCount").asInt()).isZero();
        assertThat(ids(ok("GET", "/api/reviews/mine", buyer, null, 200))).containsExactly(review);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6})
    void ratingOutsideOneToFiveIsBadRequest(int rating) throws Exception {
        advance(place(buyer, "COD"), "DELIVERED");
        expect(call("POST", reviewPath(), buyer, Map.of("rating", rating)), 400);
        assertThat(ok("GET", reviewPath(), null, null, 200).path("totalElements").asInt()).isZero();
    }

    @Test
    void complaintBelongsToCustomerAndIsHiddenFromOtherCustomers() throws Exception {
        long id = place(buyer, "COD");
        var body = Map.of("type", "OTHER", "content", "Please check the delivery instructions");
        String other = customer();
        expect(call("POST", "/api/orders/" + id + "/complaints", other, body), 404);
        long complaint = ok("POST", "/api/orders/" + id + "/complaints", buyer, body, 201).path("id").asLong();
        assertThat(ids(ok("GET", "/api/complaints", buyer, null, 200))).containsExactly(complaint);
        assertThat(ok("GET", "/api/complaints", other, null, 200).size()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"REJECTED,missing", "REJECTED,blank", "RESOLVED,missing", "RESOLVED,blank"})
    void adminCannotCloseComplaintWithoutResponse(String next, String response) throws Exception {
        long id = place(buyer, "COD");
        long complaint = ok("POST", "/api/orders/" + id + "/complaints", buyer,
                Map.of("type", "OTHER", "content", "Need assistance"), 201).path("id").asLong();
        String path = "/api/admin/complaints/" + complaint;
        if (next.equals("RESOLVED")) ok("PATCH", path, admin, Map.of("status", "PROCESSING"), 200);
        Map<String, Object> body = new HashMap<>(Map.of("status", next));
        if (response.equals("blank")) body.put("response", "   ");
        expect(call("PATCH", path, admin, body), 400);
        assertThat(ok("GET", path, admin, null, 200).path("status").asString())
                .isEqualTo(next.equals("RESOLVED") ? "PROCESSING" : "PENDING");
        body.put("response", "We have checked your request");
        assertThat(ok("PATCH", path, admin, body, 200).path("status").asString()).isEqualTo(next);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cart", "/api/orders"})
    void anonymousCustomerEndpointsAreProtected(String path) throws Exception {
        assertThat(call("GET", path, null, null).status()).isIn(401, 403);
    }

    @Test
    void customerCannotAccessAdminOrdersAndAdminCannotAccessCart() throws Exception {
        expect(call("GET", "/api/admin/orders", buyer, null), 403);
        expect(call("GET", "/api/cart", admin, null), 403);
    }

    @Test
    void twoConcurrentCheckoutsForLastItemProduceExactlyOneOrderAndNonNegativeStock() throws Exception {
        setStock(1);
        String other = customer();
        add(buyer, 1);
        add(other, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> competingCheckout(buyer, ready, start));
            var second = pool.submit(() -> competingCheckout(other, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Response a = first.get(30, TimeUnit.SECONDS);
            Response b = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(a.status(), b.status())).as("responses: %s / %s", a.body(), b.body())
                    .containsExactlyInAnyOrder(201, 400);
            assertThat(stock()).isZero();
            assertThat(myOrders(buyer).size() + myOrders(other).size()).isEqualTo(1);
            String winner = a.status() == 201 ? buyer : other;
            String loser = a.status() == 201 ? other : buyer;
            assertThat(cart(winner).path("items").size()).isZero();
            assertThat(cart(loser).path("items").path(0).path("quantity").asInt()).isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Response competingCheckout(String token, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Checkout start timed out");
        return call("POST", "/api/orders", token, orderBody());
    }
    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(JSON.writeValueAsString(body));
        var raw = mvc.perform(request).andReturn().getResponse();
        String content = raw.getContentAsString(StandardCharsets.UTF_8);
        return new Response(raw.getStatus(), content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content));
    }
    private void expect(Response response, int status) {
        assertThat(response.status()).as("response: %s", response.body()).isEqualTo(status);
    }
    private JsonNode ok(String method, String path, String token, Object body, int status) throws Exception {
        Response response = call(method, path, token, body);
        expect(response, status);
        return response.data();
    }
    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password), 200).path("token").asString();
    }
    private String customer() throws Exception {
        String email = "orders-" + UUID.randomUUID() + "@test.local";
        ok("POST", "/api/auth/register", null,
                Map.of("email", email, "password", "Passw0rd!", "fullName", "Orders parent"), 201);
        return login(email, "Passw0rd!");
    }
    private JsonNode add(String token, int quantity) throws Exception {
        return ok("POST", "/api/cart/items", token, Map.of("productId", product, "quantity", quantity), 201);
    }
    private JsonNode cart(String token) throws Exception { return ok("GET", "/api/cart", token, null, 200); }
    private JsonNode myOrders(String token) throws Exception { return ok("GET", "/api/orders", token, null, 200); }
    private JsonNode detail(String token, long id) throws Exception { return ok("GET", "/api/orders/" + id, token, null, 200); }
    private JsonNode payment(long id) throws Exception { return ok("GET", "/api/payments/" + id, buyer, null, 200); }
    private int stock() throws Exception {
        return ok("GET", "/api/admin/products/" + product, admin, null, 200).path("stockQuantity").asInt();
    }
    private void setStock(int quantity) throws Exception {
        ok("PATCH", "/api/admin/products/" + product + "/stock", admin, Map.of("stockQuantity", quantity), 200);
    }
    private Map<String, Object> orderBody() {
        return new HashMap<>(Map.of("receiverName", "Orders parent", "receiverPhone", "0901234567",
                "provinceCode", 92, "districtCode", 916, "wardCode", 31117,
                "addressDetail", "123 Test street", "paymentMethod", "COD"));
    }
    private JsonNode checkout(String token, Map<String, Object> body) throws Exception {
        return ok("POST", "/api/orders", token, body, 201);
    }
    private long place(String token, String method) throws Exception {
        add(token, 1);
        var body = orderBody();
        body.put("paymentMethod", method);
        return checkout(token, body).path("id").asLong();
    }
    private void status(long id, String state) throws Exception {
        assertThat(ok("PATCH", "/api/admin/orders/" + id + "/status", admin, Map.of("status", state), 200)
                .path("status").asString()).isEqualTo(state);
    }
    private void advance(long id, String state) throws Exception {
        if (state.equals("PENDING")) return;
        if (state.equals("CANCELLED")) { status(id, state); return; }
        for (String step : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            status(id, step);
            if (step.equals(state)) return;
        }
        throw new AssertionError("Unknown state " + state);
    }
    private long child(String token) throws Exception {
        return ok("POST", "/api/children", token,
                Map.of("name", "Orders child", "birthDate", LocalDate.now().minusYears(6).toString()), 201).path("id").asLong();
    }
    private String reviewPath() { return "/api/products/" + product + "/reviews"; }
    private List<Long> ids(JsonNode rows) {
        List<Long> values = new ArrayList<>();
        rows.forEach(row -> values.add(row.path("id").asLong()));
        return values;
    }
    private List<Double> scores(JsonNode profile) {
        List<Double> values = new ArrayList<>();
        profile.path("skillScores").forEach(row -> values.add(row.path("score").asDouble()));
        return values;
    }
    private JsonNode rowById(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Missing id " + id + " in " + rows);
    }
    private JsonNode rowByText(JsonNode rows, String key, String value) {
        for (JsonNode row : rows) if (row.path(key).asString().equals(value)) return row;
        throw new AssertionError("Missing " + value + " in " + rows);
    }
    private void configureIpn(long id, boolean signature, String amount) {
        when(gateway.verifySignature(anyMap())).thenReturn(signature);
        when(gateway.isSuccess(anyMap())).thenReturn(true);
        when(gateway.extractTxnRef(anyMap())).thenReturn(id + "-test");
        when(gateway.extractAmount(anyMap())).thenReturn(amount);
        when(gateway.extractTransactionId(anyMap())).thenReturn("orders-txn");
    }
}
