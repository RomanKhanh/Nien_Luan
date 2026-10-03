package com.brainblocks.backend.regression;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Địa chính 63 tỉnh (trước sáp nhập), báo giá phí vận chuyển theo miền, đặt hàng và địa chỉ mặc định 3 cấp.
 * Gọi HTTP thật (RANDOM_PORT) trên H2. Mã mẫu: Cần Thơ 92 / Quận Ninh Kiều 916 / Phường Cái Khế 31117,
 * Quận Ô Môn 917 (Cần Thơ) có Phường Châu Văn Liêm 31153, Quận 1 TP.HCM 760, huyện đảo Côn Đảo 755 (không có cấp xã).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocationAndShippingTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String baseUrl;
    private String adminToken;

    record Res(int status, JsonNode body, HttpResponse<String> raw) {
        JsonNode data() {
            return body.path("data");
        }

        String message() {
            return body.path("message").asString();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        adminToken = login("admin@test.local", "test-admin-password");
    }

    // ===== địa chính =====

    @Test
    void locationEndpointsArePublicAndCached() throws Exception {
        Res provinces = call("GET", "/api/locations/provinces", null, null);
        assertThat(provinces.status()).isEqualTo(200);
        assertThat(provinces.data().size()).isEqualTo(63);
        assertThat(provinces.raw().headers().firstValue("Cache-Control").orElse(""))
                .contains("max-age=86400").contains("public");
        JsonNode canTho = find(provinces.data(), 92);
        assertThat(canTho.path("name").asString()).isEqualTo("Thành phố Cần Thơ");
        assertThat(canTho.path("region").asString()).isEqualTo("MIEN_NAM");
        assertThat(canTho.path("regionLabel").asString()).isEqualTo("Miền Nam");

        JsonNode districts = call("GET", "/api/locations/provinces/92/districts", null, null).data();
        assertThat(districts.size()).isEqualTo(9);
        assertThat(find(districts, 916).path("name").asString()).isEqualTo("Quận Ninh Kiều");
        assertThat(find(districts, 916).path("hasWards").asBoolean()).isTrue();

        JsonNode wards = call("GET", "/api/locations/districts/916/wards", null, null).data();
        assertThat(find(wards, 31117).path("name").asString()).isEqualTo("Phường Cái Khế");

        // huyện đảo không có cấp xã
        JsonNode brvt = call("GET", "/api/locations/provinces/77/districts", null, null).data();
        assertThat(find(brvt, 755).path("hasWards").asBoolean()).isFalse();
        assertThat(call("GET", "/api/locations/districts/755/wards", null, null).data().size()).isZero();
    }

    @Test
    void unknownLocationCodesReturn404() throws Exception {
        assertThat(call("GET", "/api/locations/provinces/999/districts", null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/locations/districts/99999/wards", null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/locations/provinces/abc/districts", null, null).status()).isEqualTo(400);
    }

    // vùng giao hàng suy ra từ tỉnh người nhận
    @Test
    void regionOfTypicalProvinces() throws Exception {
        JsonNode provinces = call("GET", "/api/locations/provinces", null, null).data();
        Map<Integer, String> expected = Map.of(
                92, "MIEN_NAM",   // Cần Thơ
                79, "MIEN_NAM",   // TP. Hồ Chí Minh
                48, "MIEN_TRUNG", // Đà Nẵng
                68, "MIEN_TRUNG", // Lâm Đồng (Tây Nguyên)
                38, "MIEN_TRUNG", // Thanh Hóa (Bắc Trung Bộ)
                60, "MIEN_TRUNG", // Bình Thuận
                1, "MIEN_BAC");   // Hà Nội
        expected.forEach((code, region) ->
                assertThat(find(provinces, code).path("region").asString()).as("province " + code).isEqualTo(region));
        Map<String, Integer> counts = new HashMap<>();
        provinces.forEach(p -> counts.merge(p.path("region").asString(), 1, Integer::sum));
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("MIEN_NAM", 19, "MIEN_TRUNG", 19, "MIEN_BAC", 25));
    }

    // ===== báo giá =====

    @Test
    void quoteUsesCurrentCartAndRecipientRegion() throws Exception {
        String token = newCustomer();
        // giỏ rỗng: 0đ
        Res empty = call("POST", "/api/shipping/quote", token, Map.of("provinceCode", 1));
        assertThat(empty.status()).isEqualTo(200);
        assertThat(empty.data().path("totalFee").asInt()).isZero();
        assertThat(empty.data().path("parcelCount").asInt()).isZero();

        // đồ chơi 650 g, hộp 30 x 22 x 8 cm: quy đổi 880 g -> 2 bậc 0,5 kg
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        assertQuote(token, 92, "MIEN_NAM", "Miền Nam", 22000 + 4000);
        assertQuote(token, 79, "MIEN_NAM", "Miền Nam", 22000 + 4000);
        assertQuote(token, 48, "MIEN_TRUNG", "Miền Trung", 30000 + 5500);
        assertQuote(token, 1, "MIEN_BAC", "Miền Bắc", 36000 + 6500);

        // thêm 1 cái: 1.760 g quy đổi -> 4 bậc
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        JsonNode q = assertQuote(token, 92, "MIEN_NAM", "Miền Nam", 22000 + 3 * 4000);
        assertThat(q.path("provinceName").asString()).isEqualTo("Thành phố Cần Thơ");
        assertThat(q.path("actualWeightGrams").asInt()).isEqualTo(1300);
        assertThat(q.path("chargeableWeightGrams").asInt()).isEqualTo(1760);
        assertThat(q.path("parcelCount").asInt()).isEqualTo(1);
        assertThat(q.path("parcels").get(0).path("contents").get(0).path("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void quoteRequiresCustomerAndValidProvince() throws Exception {
        assertThat(call("POST", "/api/shipping/quote", null, Map.of("provinceCode", 92)).status()).isEqualTo(401);
        assertThat(call("POST", "/api/shipping/quote", adminToken, Map.of("provinceCode", 92)).status())
                .isEqualTo(403);
        String token = newCustomer();
        Res unknown = call("POST", "/api/shipping/quote", token, Map.of("provinceCode", 999));
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.message()).isEqualTo("Province code is invalid");
        Res missing = call("POST", "/api/shipping/quote", token, Map.of());
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.message()).isEqualTo("provinceCode: Vui lòng chọn tỉnh/thành");
    }

    // ===== đặt hàng =====

    @Test
    void orderStoresCodesAndNamesAtOrderTime() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        Res order = call("POST", "/api/orders", token, orderBody(92, 916, 31117, "  Số 1 Lý Tự Trọng  "));
        assertThat(order.status()).as(order.body().toString()).isEqualTo(201);
        assertThat(order.data().path("shippingAddress").asString())
                .isEqualTo("Số 1 Lý Tự Trọng, Phường Cái Khế, Quận Ninh Kiều, Thành phố Cần Thơ");
        JsonNode address = order.data().path("address");
        assertThat(address.path("provinceCode").asInt()).isEqualTo(92);
        assertThat(address.path("provinceName").asString()).isEqualTo("Thành phố Cần Thơ");
        assertThat(address.path("districtName").asString()).isEqualTo("Quận Ninh Kiều");
        assertThat(address.path("wardCode").asInt()).isEqualTo(31117);
        assertThat(address.path("wardName").asString()).isEqualTo("Phường Cái Khế");
        assertThat(address.path("addressDetail").asString()).isEqualTo("Số 1 Lý Tự Trọng");

        Order saved = orderRepository.findById(order.data().path("id").asLong()).orElseThrow();
        assertThat(saved.getShippingAddress()).isNull();
        assertThat(saved.getDistrictCode()).isEqualTo(916);
    }

    // mã sai hoặc không đúng quan hệ cha-con bị từ chối; giỏ hàng còn nguyên
    @Test
    void invalidAddressCodesAreRejected() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));

        Object[][] cases = {
                {999, 916, 31117, "Province code is invalid"},
                {92, 99999, 31117, "District code is invalid"},
                {92, 760, 26734, "District does not belong to the selected province"}, // Quận 1 thuộc TP.HCM
                {92, 916, 99999999, "Ward code is invalid"},
                {92, 916, 31153, "Ward does not belong to the selected district"},     // phường của Quận Ô Môn
                {92, 916, null, "Ward is required"},
                {77, 755, 26506, "This district has no wards"},                         // Côn Đảo không có cấp xã
        };
        for (Object[] c : cases) {
            Res res = call("POST", "/api/orders", token, orderBody(c[0], c[1], c[2], "Số 1"));
            assertThat(res.status()).as(Arrays.toString(c)).isEqualTo(400);
            assertThat(res.message()).isEqualTo(c[3]);
        }
        Res noProvince = call("POST", "/api/orders", token, orderBody(null, 916, 31117, "Số 1"));
        assertThat(noProvince.message()).isEqualTo("provinceCode: Vui lòng chọn tỉnh/thành");
        Res noDetail = call("POST", "/api/orders", token, orderBody(92, 916, 31117, "   "));
        assertThat(noDetail.message()).isEqualTo("addressDetail: Vui lòng nhập địa chỉ chi tiết");
        Res longDetail = call("POST", "/api/orders", token, orderBody(92, 916, 31117, "x".repeat(256)));
        assertThat(longDetail.message()).isEqualTo("addressDetail: Địa chỉ chi tiết tối đa 255 ký tự");

        assertThat(call("GET", "/api/cart", token, null).data().path("items").size()).isEqualTo(1);

        // huyện đảo: không cần mã xã
        Res island = call("POST", "/api/orders", token, orderBody(77, 755, null, "Số 2 Nguyễn Huệ"));
        assertThat(island.status()).as(island.body().toString()).isEqualTo(201);
        assertThat(island.data().path("shippingAddress").asString())
                .isEqualTo("Số 2 Nguyễn Huệ, Huyện Côn Đảo, Tỉnh Bà Rịa - Vũng Tàu");
        assertThat(island.data().path("address").path("wardCode").isNull()).isTrue();
    }

    // đơn đặt trước khi có địa chỉ 3 cấp vẫn hiển thị chuỗi địa chỉ cũ
    @Test
    void legacyOrderShowsFreeTextAddress() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        long orderId = call("POST", "/api/orders", token, orderBody(92, 916, 31117, "Số 1")).data().path("id").asLong();
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setShippingAddress("12 Nguyễn Trãi, Q.5, TP.HCM");
            order.setProvinceCode(null);
            order.setProvinceName(null);
            order.setDistrictCode(null);
            order.setDistrictName(null);
            order.setWardCode(null);
            order.setWardName(null);
            order.setAddressDetail(null);
        });
        JsonNode legacy = call("GET", "/api/orders/" + orderId, token, null).data();
        assertThat(legacy.path("shippingAddress").asString()).isEqualTo("12 Nguyễn Trãi, Q.5, TP.HCM");
        assertThat(legacy.path("address").isNull()).isTrue();
        JsonNode adminView = call("GET", "/api/admin/orders/" + orderId, adminToken, null).data();
        assertThat(adminView.path("shippingAddress").asString()).isEqualTo("12 Nguyễn Trãi, Q.5, TP.HCM");
    }

    // ===== phí vận chuyển trong đơn hàng =====

    // tổng = tiền hàng + phí ship; server tự tính, bỏ qua mọi số tiền client tự gửi
    @Test
    void orderTotalIsSubtotalPlusServerComputedShipping() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8); // giá 100.000, 880 g quy đổi -> Miền Nam 26.000
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        Map<String, Object> body = orderBody(92, 916, 31117, "Số 1");
        body.put("shippingFee", 0);
        body.put("totalAmount", 1);
        body.put("subtotal", 1);
        Res order = call("POST", "/api/orders", token, body);
        assertThat(order.status()).as(order.body().toString()).isEqualTo(201);
        JsonNode o = order.data();
        assertThat(o.path("subtotal").asInt()).isEqualTo(100_000);
        assertThat(o.path("shippingFee").asInt()).isEqualTo(26_000);
        assertThat(o.path("totalAmount").asInt()).isEqualTo(126_000);
        assertThat(o.path("shippingZone").asString()).isEqualTo("MIEN_NAM");
        assertThat(o.path("shippingZoneLabel").asString()).isEqualTo("Miền Nam");
        assertThat(o.path("parcelCount").asInt()).isEqualTo(1);

        // admin: chi tiết và danh sách cũng có đủ các dòng tiền
        long orderId = o.path("id").asLong();
        JsonNode admin = call("GET", "/api/admin/orders/" + orderId, adminToken, null).data();
        assertThat(admin.path("shippingFee").asInt()).isEqualTo(26_000);
        assertThat(admin.path("totalAmount").asInt()).isEqualTo(126_000);
        JsonNode summary = findById(call("GET", "/api/admin/orders?size=100", adminToken, null).data().path("content"),
                orderId);
        assertThat(summary.path("subtotal").asInt()).isEqualTo(100_000);
        assertThat(summary.path("shippingFee").asInt()).isEqualTo(26_000);
        assertThat(summary.path("totalAmount").asInt()).isEqualTo(126_000);
    }

    // phí khách thấy khác phí server tính (giỏ đổi giữa lúc báo giá và lúc đặt) -> 409 kèm phí mới, không tạo đơn
    @Test
    void mismatchedExpectedShippingFeeReturns409() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        int quoted = call("POST", "/api/shipping/quote", token, Map.of("provinceCode", 92)).data()
                .path("totalFee").asInt();
        assertThat(quoted).isEqualTo(26_000);
        // giỏ đổi: thêm 1 cái -> 1.760 g -> 34.000
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        int stockBefore = stock(productId);

        Map<String, Object> body = orderBody(92, 916, 31117, "Số 1");
        body.put("expectedShippingFee", quoted);
        Res conflict = call("POST", "/api/orders", token, body);
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.message()).contains("Phí vận chuyển đã thay đổi thành 34.000₫")
                .contains("tổng thanh toán 234.000₫");
        assertThat(conflict.data().path("shippingFee").asInt()).isEqualTo(34_000);
        assertThat(conflict.data().path("subtotal").asInt()).isEqualTo(200_000);
        assertThat(conflict.data().path("totalAmount").asInt()).isEqualTo(234_000);
        // không trừ kho, giỏ còn nguyên
        assertThat(stock(productId)).isEqualTo(stockBefore);
        assertThat(call("GET", "/api/cart", token, null).data().path("items").get(0).path("quantity").asInt())
                .isEqualTo(2);

        // khách xác nhận phí mới
        body.put("expectedShippingFee", 34_000);
        Res ok = call("POST", "/api/orders", token, body);
        assertThat(ok.status()).as(ok.body().toString()).isEqualTo(201);
        assertThat(ok.data().path("totalAmount").asInt()).isEqualTo(234_000);
        assertThat(stock(productId)).isEqualTo(stockBefore - 2);
    }

    // đơn trước khi có phí ship (subtotal / shippingFee null): hiển thị tiền hàng = tổng, phí ship 0
    @Test
    void legacyOrderWithoutShippingFeeIsReadable() throws Exception {
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        long orderId = call("POST", "/api/orders", token, orderBody(92, 916, 31117, "Số 1")).data().path("id").asLong();
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setTotalAmount(new BigDecimal("100000"));
            order.setSubtotal(null);
            order.setShippingFee(null);
            order.setShippingZone(null);
            order.setParcelCount(null);
        });
        JsonNode legacy = call("GET", "/api/orders/" + orderId, token, null).data();
        assertThat(legacy.path("subtotal").asInt()).isEqualTo(100_000);
        assertThat(legacy.path("shippingFee").asInt()).isZero();
        assertThat(legacy.path("totalAmount").asInt()).isEqualTo(100_000);
        assertThat(legacy.path("shippingZone").isNull()).isTrue();
        assertThat(legacy.path("parcelCount").isNull()).isTrue();
        assertThat(call("GET", "/api/admin/orders?size=100", adminToken, null).status()).isEqualTo(200);
    }

    // doanh thu chỉ tính tiền hàng; phí ship đã thu thống kê riêng
    @Test
    void revenueExcludesShippingFee() throws Exception {
        JsonNode before = call("GET", "/api/admin/stats", adminToken, null).data();
        String token = newCustomer();
        long productId = createProduct(650, 30, 22, 8);
        call("POST", "/api/cart/items", token, Map.of("productId", productId, "quantity", 1));
        long orderId = call("POST", "/api/orders", token, orderBody(1, 1, 1, "Số 1")).data().path("id").asLong();
        for (String status : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            assertThat(call("PATCH", "/api/admin/orders/" + orderId + "/status", adminToken, Map.of("status", status))
                    .status()).isEqualTo(200);
        }
        JsonNode after = call("GET", "/api/admin/stats", adminToken, null).data();
        // Hà Nội (Miền Bắc): 36.000 + 6.500 = 42.500
        assertThat(after.path("deliveredRevenue").decimalValue()
                .subtract(before.path("deliveredRevenue").decimalValue())).isEqualByComparingTo("100000");
        assertThat(after.path("deliveredShippingFees").decimalValue()
                .subtract(before.path("deliveredShippingFees").decimalValue())).isEqualByComparingTo("42500");
    }

    // ===== địa chỉ mặc định trong hồ sơ =====

    @Test
    void profileStoresDefaultAddressForCheckout() throws Exception {
        String token = newCustomer();
        Res saved = call("PUT", "/api/me", token, profileBody(79, 760, 26734, " 10 Lê Lợi "));
        assertThat(saved.status()).as(saved.body().toString()).isEqualTo(200);
        JsonNode address = saved.data().path("defaultShippingAddress");
        assertThat(address.path("provinceCode").asInt()).isEqualTo(79);
        assertThat(address.path("districtName").asString()).isEqualTo("Quận 1");
        assertThat(address.path("wardName").asString()).isEqualTo("Phường Tân Định");
        assertThat(address.path("addressDetail").asString()).isEqualTo("10 Lê Lợi");
        assertThat(saved.data().path("defaultAddress").asString())
                .isEqualTo("10 Lê Lợi, Phường Tân Định, Quận 1, Thành phố Hồ Chí Minh");
        assertThat(call("GET", "/api/me", token, null).data().path("defaultShippingAddress").path("wardCode").asInt())
                .isEqualTo(26734);

        // sai quan hệ cha-con / thiếu địa chỉ chi tiết bị từ chối, dữ liệu cũ giữ nguyên
        Res wrong = call("PUT", "/api/me", token, profileBody(79, 916, 31117, "10 Lê Lợi"));
        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.message()).isEqualTo("District does not belong to the selected province");
        Res noDetail = call("PUT", "/api/me", token, profileBody(79, 760, 26734, " "));
        assertThat(noDetail.message()).isEqualTo("Address detail is required");
        assertThat(call("GET", "/api/me", token, null).data().path("defaultShippingAddress").path("districtCode").asInt())
                .isEqualTo(760);

        // để trống cả 4 field = xóa địa chỉ mặc định
        Res cleared = call("PUT", "/api/me", token, profileBody(null, null, null, null));
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.data().path("defaultShippingAddress").isNull()).isTrue();
        assertThat(cleared.data().path("defaultAddress").isNull()).isTrue();
    }

    // ===== helpers =====

    private JsonNode assertQuote(String token, int provinceCode, String zone, String label, int total)
            throws Exception {
        Res res = call("POST", "/api/shipping/quote", token, Map.of("provinceCode", provinceCode));
        assertThat(res.status()).as(res.body().toString()).isEqualTo(200);
        assertThat(res.data().path("zone").asString()).isEqualTo(zone);
        assertThat(res.data().path("zoneLabel").asString()).isEqualTo(label);
        assertThat(res.data().path("totalFee").asInt()).as("province " + provinceCode).isEqualTo(total);
        return res.data();
    }

    private static Map<String, Object> orderBody(Object province, Object district, Object ward, String detail) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverName", "Test");
        body.put("receiverPhone", "0901234567");
        body.put("provinceCode", province);
        body.put("districtCode", district);
        body.put("wardCode", ward);
        body.put("addressDetail", detail);
        body.put("paymentMethod", "COD");
        return body;
    }

    private static Map<String, Object> profileBody(Object province, Object district, Object ward, String detail) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Test User");
        body.put("defaultProvinceCode", province);
        body.put("defaultDistrictCode", district);
        body.put("defaultWardCode", ward);
        body.put("defaultAddressDetail", detail);
        return body;
    }

    private static JsonNode find(JsonNode list, int code) {
        for (JsonNode node : list) {
            if (node.path("code").asInt() == code) {
                return node;
            }
        }
        throw new AssertionError("code " + code + " not found");
    }

    private static JsonNode findById(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("id " + id + " not found");
    }

    private int stock(long productId) throws Exception {
        return call("GET", "/api/admin/products/" + productId, adminToken, null).data().path("stockQuantity").asInt();
    }

    private long createProduct(int grams, int length, int width, int height) throws Exception {
        Res category = call("POST", "/api/admin/categories", adminToken,
                Map.of("name", "ship-" + SEQ.incrementAndGet() + "-" + System.nanoTime()));
        assertThat(category.status()).isEqualTo(201);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "[TEST] Ship " + SEQ.incrementAndGet());
        body.put("price", 100000);
        body.put("stockQuantity", 50);
        body.put("minAge", 3);
        body.put("maxAge", 12);
        body.put("categoryId", category.data().path("id").asLong());
        body.put("weightGrams", grams);
        body.put("lengthCm", length);
        body.put("widthCm", width);
        body.put("heightCm", height);
        body.put("skillImpacts", new ArrayList<>());
        Res res = call("POST", "/api/admin/products", adminToken, body);
        assertThat(res.status()).as(res.body().toString()).isEqualTo(201);
        return res.data().path("id").asLong();
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
        return new Res(r.statusCode(), node, r);
    }

    private String login(String email, String password) throws Exception {
        Res r = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(r.status()).as("login " + email).isEqualTo(200);
        return r.data().path("token").asString();
    }

    private String newCustomer() throws Exception {
        String email = "ls" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Test User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        return login(email, PASSWORD);
    }
}
