package com.brainblocks.backend.fulltest.users;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.mail.MailService;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full application and real JWT/security chain; all persistence uses the test H2 profile. */
@SpringBootTest
@ActiveProfiles("test")
class UsersChildrenApiTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final JsonMapper JSON = new JsonMapper();
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    private MockMvc mvc;

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }
    record Account(String email, String token, long id) {}

    @BeforeEach
    void setUp() {
        // No spring-security-test dependency: install the actual application's filter chain.
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void registrationCreatesCustomerWithoutPasswordInResponse() throws Exception {
        String email = email();
        Response r = register(email);
        expect(r, 201);
        assertThat(r.data().path("email").asString()).isEqualTo(email);
        assertThat(r.data().path("role").asString()).isEqualTo("CUSTOMER");
        assertThat(r.data().path("id").asLong()).isPositive();
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = email();
        expect(register(email), 201);
        expect(register(email.toUpperCase()), 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "", "1234567"})
    void shortOrBlankRegistrationPasswordIsRejected(String password) throws Exception {
        expect(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Parent", "email", email(), "password", password)), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fullName", "email", "password"})
    void missingRegistrationFieldIsRejected(String field) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("fullName", "Parent", "email", email(), "password", PASSWORD));
        body.remove(field);
        expect(call("POST", "/api/auth/register", null, body), 400);
    }

    @Test
    void loginReturnsJwtThatAuthenticatesMe() throws Exception {
        Account a = customer();
        assertThat(a.token().split("\\.")).hasSize(3);
        Response me = call("GET", "/api/me", a.token(), null);
        expect(me, 200);
        assertThat(me.data().path("email").asString()).isEqualTo(a.email());
        assertThat(me.data().path("id").asLong()).isEqualTo(a.id());
    }

    @Test
    void wrongLoginPasswordIsUnauthorized() throws Exception {
        Account a = customer();
        expect(login(a.email(), "WrongPass123!"), 401);
    }

    @Test
    void lockedCustomerCannotLoginOrUseExistingTokenAndCanBeUnlocked() throws Exception {
        Account a = customer();
        String admin = admin();
        Response locked = call("PATCH", "/api/admin/users/" + a.id() + "/status", admin, Map.of("enabled", false));
        expect(locked, 200);
        assertThat(locked.data().path("enabled").asBoolean()).isFalse();
        expect(login(a.email(), PASSWORD), 403);
        expect(call("GET", "/api/me", a.token(), null), 401);
        expect(call("PATCH", "/api/admin/users/" + a.id() + "/status", admin, Map.of("enabled", true)), 200);
        Response fresh = login(a.email(), PASSWORD);
        expect(fresh, 200);
        expect(call("GET", "/api/me", fresh.data().path("token").asString(), null), 200);
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        expect(call("GET", "/api/me", "not-a-jwt", null), 401);
    }

    @Test
    void tokenWithTamperedSignatureIsUnauthorized() throws Exception {
        String token = customer().token();
        int signature = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? "B" : "A") + token.substring(signature + 1);
        expect(call("GET", "/api/me", tampered, null), 401);
    }

    @Test
    void changedPasswordRevokesOldJwtAndRequiresNewPassword() throws Exception {
        Account a = customer();
        expect(call("PUT", "/api/me/password", a.token(),
                Map.of("currentPassword", PASSWORD, "newPassword", "NewPassw0rd!")), 200);
        expect(call("GET", "/api/me", a.token(), null), 401);
        expect(login(a.email(), PASSWORD), 401);
        Response login = login(a.email(), "NewPassw0rd!");
        expect(login, 200);
        expect(call("GET", "/api/me", login.data().path("token").asString(), null), 200);
    }

    @Test
    void meUpdatesNameAndPhoneAndPersistsThem() throws Exception {
        Account a = customer();
        expect(call("PUT", "/api/me", a.token(), Map.of("fullName", "Updated Parent", "phone", "0901234567")), 200);
        Response me = call("GET", "/api/me", a.token(), null);
        expect(me, 200);
        assertThat(me.data().path("fullName").asString()).isEqualTo("Updated Parent");
        assertThat(me.data().path("phone").asString()).isEqualTo("0901234567");
        assertThat(me.data().path("email").asString()).isEqualTo(a.email());
    }

    @Test
    void incorrectCurrentPasswordDoesNotChangePasswordOrInvalidateJwt() throws Exception {
        Account a = customer();
        expect(call("PUT", "/api/me/password", a.token(),
                Map.of("currentPassword", "Incorrect!", "newPassword", "NewPassw0rd!")), 400);
        expect(call("GET", "/api/me", a.token(), null), 200);
        expect(login(a.email(), PASSWORD), 200);
    }

    @Test
    void childCrudPersistsUpdatesAndDeletion() throws Exception {
        Account a = customer();
        long child = child(a.token(), "First");
        Response read = call("GET", "/api/children/" + child, a.token(), null);
        expect(read, 200);
        assertThat(read.data().path("name").asString()).isEqualTo("First");
        Map<String, Object> update = Map.of("name", "Updated", "birthDate", "2019-02-03", "gender", "FEMALE", "note", "Loves STEM");
        expect(call("PUT", "/api/children/" + child, a.token(), update), 200);
        Response updated = call("GET", "/api/children/" + child, a.token(), null);
        expect(updated, 200);
        for (var field : update.entrySet()) {
            assertThat(updated.data().path(field.getKey()).asString()).isEqualTo(field.getValue());
        }
        expect(call("DELETE", "/api/children/" + child, a.token(), null), 200);
        expect(call("GET", "/api/children/" + child, a.token(), null), 404);
        assertThat(call("GET", "/api/children", a.token(), null).data().size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "birthDate", "gender"})
    void invalidChildFieldsAreRejectedOnCreateAndUpdate(String field) throws Exception {
        Account a = customer();
        long child = child(a.token(), "Valid");
        var invalid = new java.util.HashMap<String, Object>(childBody("Valid"));
        invalid.put(field, switch (field) {
            case "name" -> " ";
            case "birthDate" -> LocalDate.now().plusDays(1).toString();
            default -> "ALIEN";
        });
        expect(call("POST", "/api/children", a.token(), invalid), 400);
        expect(call("PUT", "/api/children/" + child, a.token(), invalid), 400);
        assertThat(call("GET", "/api/children/" + child, a.token(), null).data().path("name").asString()).isEqualTo("Valid");
    }

    @Test
    void eachParentListsOnlyTheirOwnChildren() throws Exception {
        Account a = customer();
        Account b = customer();
        long first = child(a.token(), "First");
        long second = child(a.token(), "Second");
        long other = child(b.token(), "Other");
        assertThat(childIds(a.token())).containsExactlyInAnyOrder(first, second);
        assertThat(childIds(b.token())).containsExactly(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/me", "/api/children"})
    void anonymousAccessIsRejected(String path) throws Exception {
        assertThat(call("GET", path, null, null).status()).isIn(401, 403);
    }

    @ParameterizedTest(name = "other parent: {0} {1}")
    @CsvSource({"GET, ''", "PUT, ''", "DELETE, ''", "GET, /products", "POST, /products",
            "DELETE, /products/{assignment}", "GET, /skill-profile", "GET, /wishlist",
            "PUT, /wishlist/{product}", "DELETE, /wishlist/{product}"})
    void otherParentCannotAccessOrMutateChild(String method, String suffix) throws Exception {
        Account a = customer();
        Account b = customer();
        long child = child(a.token(), "Protected");
        long product = product();
        Response assignment = call("POST", "/api/children/" + child + "/products", a.token(), Map.of("productId", product));
        expect(assignment, 201);
        expect(call("PUT", "/api/children/" + child + "/wishlist/" + product, a.token(), null), 200);
        String path = "/api/children/" + child + suffix.replace("{product}", Long.toString(product))
                .replace("{assignment}", Long.toString(assignment.data().path("id").asLong()));
        Object body = method.equals("PUT") && suffix.isEmpty() ? childBody("Hacked")
                : method.equals("POST") ? Map.of("productId", product) : null;
        expect(call(method, path, b.token(), body), 404);
        Response owned = call("GET", "/api/children/" + child, a.token(), null);
        expect(owned, 200);
        assertThat(owned.data().path("name").asString()).isEqualTo("Protected");
        assertThat(call("GET", "/api/children/" + child + "/products", a.token(), null).data().size()).isEqualTo(1);
        assertThat(call("GET", "/api/children/" + child + "/wishlist", a.token(), null).data().size()).isEqualTo(1);
    }

    @Test
    void adminCannotCreateChildProfile() throws Exception {
        expect(call("POST", "/api/children", admin(), childBody("Admin child")), 403);
    }

    @Test
    void productAssignmentRejectsDuplicatesAndRemovalPersists() throws Exception {
        String token = customer().token();
        long child = child(token, "Builder");
        long product = product();
        Response assigned = call("POST", "/api/children/" + child + "/products", token, Map.of("productId", product));
        expect(assigned, 201);
        assertThat(assigned.data().path("productId").asLong()).isEqualTo(product);
        expect(call("POST", "/api/children/" + child + "/products", token, Map.of("productId", product)), 400);
        Response products = call("GET", "/api/children/" + child + "/products", token, null);
        expect(products, 200);
        assertThat(products.data().size()).isEqualTo(1);
        expect(call("DELETE", "/api/children/" + child + "/products/" + assigned.data().path("id").asLong(), token, null), 200);
        assertThat(call("GET", "/api/children/" + child + "/products", token, null).data().size()).isZero();
    }

    @Test
    void removingAssignmentOfAnotherChildReturns404() throws Exception {
        String token = customer().token();
        long first = child(token, "First");
        long second = child(token, "Second");
        Response assignment = call("POST", "/api/children/" + first + "/products", token, Map.of("productId", product()));
        expect(assignment, 201);
        expect(call("DELETE", "/api/children/" + second + "/products/" + assignment.data().path("id").asLong(), token, null), 404);
        assertThat(call("GET", "/api/children/" + first + "/products", token, null).data().size()).isEqualTo(1);
        expect(call("DELETE", "/api/children/" + second + "/products/9223372036854775807", token, null), 404);
    }

    @Test
    void ordinaryCustomerCannotListAdminUsers() throws Exception {
        expect(call("GET", "/api/admin/users", customer().token(), null), 403);
    }

    @Test
    void adminListsSearchesAndReadsCustomerDetailsWithoutPassword() throws Exception {
        Account a = customer();
        customer();
        String admin = admin();
        Response list = call("GET", "/api/admin/users?size=100", admin, null);
        expect(list, 200);
        assertThat(list.data().path("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        for (JsonNode user : list.data().path("content")) {
            assertThat(user.path("role").asString()).isEqualTo("CUSTOMER");
        }
        String keyword = a.email().substring(0, a.email().indexOf('@')).toUpperCase();
        Response search = call("GET", "/api/admin/users?keyword=" + keyword, admin, null);
        expect(search, 200);
        assertThat(search.data().path("totalElements").asLong()).isEqualTo(1);
        assertThat(search.data().path("content").path(0).path("id").asLong()).isEqualTo(a.id());
        Response detail = call("GET", "/api/admin/users/" + a.id(), admin, null);
        expect(detail, 200);
        assertThat(detail.data().path("email").asString()).isEqualTo(a.email());
        Response absent = call("GET", "/api/admin/users?keyword=no-match-" + UUID.randomUUID(), admin, null);
        expect(absent, 200);
        assertThat(absent.data().path("content").size()).isZero();
    }

    @Test
    void adminCannotLockAdminAccount() throws Exception {
        String admin = admin();
        Response me = call("GET", "/api/me", admin, null);
        expect(me, 200);
        expect(call("PATCH", "/api/admin/users/" + me.data().path("id").asLong() + "/status", admin, Map.of("enabled", false)), 400);
        expect(call("GET", "/api/me", admin, null), 200);
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(JSON.writeValueAsString(body));
        var result = mvc.perform(request).andReturn().getResponse();
        String content = result.getContentAsString();
        JsonNode json = content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content);
        // Validation errors may name the invalid password field, but successful DTOs must never expose it.
        if (result.getStatus() < 400) {
            assertThat(content).doesNotContainIgnoringCase("\"password\"", "\"currentPassword\"", "\"newPassword\"");
        }
        return new Response(result.getStatus(), json);
    }

    private void expect(Response response, int status) {
        assertThat(response.status()).as("response: %s", response.body()).isEqualTo(status);
    }
    private String email() { return "users-" + UUID.randomUUID() + "@test.local"; }
    private Response register(String email) throws Exception {
        return call("POST", "/api/auth/register", null, Map.of("fullName", "STEM Parent", "email", email, "password", PASSWORD));
    }
    private Response login(String email, String password) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
    }
    private Account customer() throws Exception {
        String email = email();
        expect(register(email), 201);
        Response login = login(email, PASSWORD);
        expect(login, 200);
        return new Account(email, login.data().path("token").asString(), login.data().path("userId").asLong());
    }
    private String admin() throws Exception {
        Response r = login("admin@test.local", "test-admin-password");
        expect(r, 200);
        return r.data().path("token").asString();
    }
    private Map<String, Object> childBody(String name) {
        return Map.of("name", name, "birthDate", "2020-01-02", "gender", "MALE");
    }
    private long child(String token, String name) throws Exception {
        Response r = call("POST", "/api/children", token, childBody(name));
        expect(r, 201);
        return r.data().path("id").asLong();
    }
    private List<Long> childIds(String token) throws Exception {
        Response r = call("GET", "/api/children", token, null);
        expect(r, 200);
        List<Long> ids = new ArrayList<>();
        r.data().forEach(child -> ids.add(child.path("id").asLong()));
        return ids;
    }
    private long product() {
        return new TransactionTemplate(transactions).execute(status -> {
            Category category = Category.builder().name("Users test " + UUID.randomUUID()).build();
            em.persist(category);
            Product product = Product.builder().name("STEM blocks").category(category)
                    .price(new BigDecimal("100000.00")).stockQuantity(10).minAge(3).maxAge(12)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            em.persist(product);
            return product.getId();
        });
    }
}
