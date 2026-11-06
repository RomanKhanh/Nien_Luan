package com.brainblocks.backend.fulltest.catalog;

import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.ai.ProductEmbeddingService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real controllers, repositories, JWT and security filters, with rollback isolation on H2. */
@SpringBootTest(properties = "app.skill-score.alpha=0.5")
@ActiveProfiles("test")
@Transactional
class CatalogApiTest {
    private static final JsonMapper JSON = new JsonMapper();
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired EntityManager em;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    @MockitoBean ProductEmbeddingService productEmbeddingService;
    private MockMvc mvc;
    private String admin;
    private long category;
    private final Map<String, Long> skills = new HashMap<>();

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        // Seeded products cannot affect ranking or pagination; the transaction restores them afterwards.
        em.createQuery("update Product p set p.active = false").executeUpdate();
        em.clear();
        admin = login("admin@test.local", "test-admin-password");
        category = createCategory("Catalog " + UUID.randomUUID());
        for (JsonNode skill : ok("GET", "/api/skills", null, null, 200)) {
            skills.put(skill.path("code").asString(), skill.path("id").asLong());
        }
    }

    @Test
    void publicPaginationHasStableOrderAndExcludesHiddenProducts() throws Exception {
        long first = product("First", 100, 3, 8, 10, Map.of());
        long second = product("Second", 200, 3, 8, 10, Map.of());
        long hidden = product("Hidden", 300, 3, 8, 10, Map.of());
        ok("DELETE", "/api/admin/products/" + hidden, admin, null, 200);
        JsonNode page0 = search("sort=price_asc&page=0&size=1");
        JsonNode page1 = search("sort=price_asc&page=1&size=1");
        assertThat(ids(page0.path("content"), "id")).containsExactly(first);
        assertThat(ids(page1.path("content"), "id")).containsExactly(second);
        assertThat(page0.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page1.path("page").asInt()).isEqualTo(1);
        assertThat(search("page=2&size=1").path("content").size()).isZero();
        JsonNode adminPage = ok("GET", "/api/admin/products?categoryId=" + category, admin, null, 200);
        assertThat(ids(adminPage.path("content"), "id")).contains(first, second, hidden);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=-1", "size=0"})
    void invalidPaginationIsBadRequest(String query) throws Exception {
        expect(call("GET", "/api/products?" + query, null, null), 400);
    }

    @Test
    void oversizedSizeIsCappedAndLargePageIsEmpty() throws Exception {
        product("One", 100, 3, 8, 10, Map.of());
        assertThat(search("size=2147483647").path("size").asInt()).isEqualTo(100);
        JsonNode far = search("page=2147483647&size=100");
        assertThat(far.path("content").size()).isZero();
        assertThat(far.path("totalElements").asLong()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lắp ráp", "lap rap", "LAP RAP"})
    void searchMatchesVietnameseWithAndWithoutAccents(String keyword) throws Exception {
        long found = product("Bộ lắp ráp robot", 100, 3, 8, 10, Map.of());
        product("Puzzle", 100, 3, 8, 10, Map.of());
        JsonNode result = query("/api/products", "keyword", keyword, null);
        assertThat(ids(result.path("content"), "id")).containsExactly(found);
    }

    @Test
    void searchAlsoMatchesDescription() throws Exception {
        var body = body("Robot", 100, 3, 8, 10, Map.of());
        body.put("description", "Phát triển tư duy không gian");
        long id = ok("POST", "/api/admin/products", admin, body, 201).path("id").asLong();
        assertThat(ids(query("/api/products", "keyword", "tu duy khong gian", null).path("content"), "id"))
                .containsExactly(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\", "' OR 1=1 --", "<script>alert(1)</script>", "🧩"})
    void specialKeywordsAreSafeAndDoNotActAsWildcards(String keyword) throws Exception {
        product("Ordinary blocks", 100, 3, 8, 10, Map.of());
        assertThat(query("/api/products", "keyword", keyword, null).path("totalElements").asLong()).isZero();
        assertThat(query("/api/products/suggestions", "q", keyword, null).isArray()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lắp ráp", "lap rap"})
    void suggestionsPreserveAccentsAndOmitHiddenProducts(String keyword) throws Exception {
        long visible = product("Bộ lắp ráp robot", 100, 3, 8, 10, Map.of());
        long hidden = product("Bộ lắp ráp bí mật", 100, 3, 8, 10, Map.of());
        ok("DELETE", "/api/admin/products/" + hidden, admin, null, 200);
        JsonNode result = query("/api/products/suggestions", "q", keyword, null);
        assertThat(ids(result, "id")).contains(visible).doesNotContain(hidden);
        assertThat(row(result, "id", visible).path("label").asString()).isEqualTo("Bộ lắp ráp robot");
        assertThat(query("/api/products/suggestions", "q", "   ", null).size()).isZero();
    }

    @Test
    void ageFilterIncludesOverlappingEndpoints() throws Exception {
        long left = product("Left", 100, 3, 5, 10, Map.of());
        long right = product("Right", 100, 8, 12, 10, Map.of());
        product("Outside", 100, 9, 12, 10, Map.of());
        assertThat(ids(search("ageFrom=5&ageTo=8").path("content"), "id"))
                .containsExactlyInAnyOrder(left, right);
        assertThat(ids(search("age=5").path("content"), "id")).containsExactly(left);
    }

    @Test
    void skillFilterUsesAnySelectedSkillAndThresholdFive() throws Exception {
        long logic = product("Logic", 100, 3, 8, 10, Map.of("LOGIC", 5));
        long stem = product("Stem", 100, 3, 8, 10, Map.of("STEM", 8));
        product("Weak", 100, 3, 8, 10, Map.of("LOGIC", 4, "STEM", 4));
        assertThat(ids(search("skills=LOGIC,STEM").path("content"), "id"))
                .containsExactlyInAnyOrder(logic, stem);
        assertThat(ids(search("skills=logic&skills=STEM").path("content"), "id"))
                .containsExactlyInAnyOrder(logic, stem);
        assertThat(search("skills=UNKNOWN_SKILL").path("totalElements").asLong()).isZero();
    }

    @Test
    void priceCategoryAndStockFiltersWorkSeparatelyAndTogether() throws Exception {
        long cheap = product("Cheap", 100, 3, 8, 10, Map.of("LOGIC", 8));
        long middle = product("Middle", 200, 3, 8, 10, Map.of("STEM", 8));
        long soldOut = product("Sold out", 200, 3, 8, 0, Map.of("LOGIC", 8));
        long expensive = product("Expensive", 300, 3, 8, 10, Map.of());
        assertThat(ids(search("minPrice=100&maxPrice=200").path("content"), "id"))
                .containsExactlyInAnyOrder(cheap, middle, soldOut);
        assertThat(ids(search("inStock=true").path("content"), "id"))
                .containsExactlyInAnyOrder(cheap, middle, expensive);
        long otherCategory = createCategory("Other " + UUID.randomUUID());
        var other = body("Other category", 150, 3, 8, 10, Map.of("STEM", 9));
        other.put("categoryId", otherCategory);
        long excluded = ok("POST", "/api/admin/products", admin, other, 201).path("id").asLong();
        assertThat(ids(search("inStock=true&ageFrom=5&ageTo=6&skills=LOGIC,STEM&minPrice=150&maxPrice=200")
                .path("content"), "id")).containsExactly(middle);
        assertThat(ids(search("").path("content"), "id")).doesNotContain(excluded);
    }

    @Test
    void reversedPriceRangeIsBadRequest() throws Exception {
        expect(call("GET", "/api/products?minPrice=200&maxPrice=100", null, null), 400);
    }

    // BUG: ProductService.search does not validate ageFrom > ageTo; broad-age products still match.
    @Test
    void reversedAgeRangeIsBadRequest() throws Exception {
        product("Broad ages", 100, 0, 18, 10, Map.of());
        expect(call("GET", "/api/products?ageFrom=10&ageTo=3", null, null), 400);
    }

    @ParameterizedTest
    @CsvSource({"price_asc, true", "price_desc, false", "newest, false", "relevance, false"})
    void sortingReturnsExpectedOrder(String sort, boolean ascending) throws Exception {
        long first = product("First", 100, 3, 8, 10, Map.of("LOGIC", 5));
        long second = product("Second", 300, 3, 8, 10, Map.of("LOGIC", 9));
        assertThat(ids(search("sort=" + sort).path("content"), "id"))
                .containsExactly(ascending ? first : second, ascending ? second : first);
    }

    @Test
    void unknownSortIsBadRequest() throws Exception {
        expect(call("GET", "/api/products?sort=unexpected", null, null), 400);
    }

    @Test
    void detailIncludesPriceAgesAndAllFourImpacts() throws Exception {
        Map<String, Integer> impacts = Map.of("LOGIC", 8, "STEM", 7, "CREATIVE", 6, "PROBLEM_SOLVING", 5);
        long id = product("Four skills", 123456, 4, 9, 12, impacts);
        JsonNode detail = ok("GET", "/api/products/" + id, null, null, 200);
        assertThat(detail.path("price").asDouble()).isEqualTo(123456);
        assertThat(detail.path("minAge").asInt()).isEqualTo(4);
        assertThat(detail.path("maxAge").asInt()).isEqualTo(9);
        assertThat(detail.path("skillImpacts").size()).isEqualTo(4);
        for (var entry : impacts.entrySet()) {
            assertThat(row(detail.path("skillImpacts"), "skillId", skills.get(entry.getKey()))
                    .path("impactIndex").asInt()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void missingAndHiddenDetailsReturn404() throws Exception {
        expect(call("GET", "/api/products/9223372036854775807", null, null), 404);
        long id = product("Hidden", 100, 3, 8, 10, Map.of());
        ok("DELETE", "/api/admin/products/" + id, admin, null, 200);
        expect(call("GET", "/api/products/" + id, null, null), 404);
        assertThat(ok("GET", "/api/admin/products/" + id, admin, null, 200).path("active").asBoolean()).isFalse();
    }

    @Test
    void adminUpdatesProductStockAndReplacesImpactsThenRestoresHiddenProduct() throws Exception {
        long id = product("Original", 100, 3, 8, 10, Map.of("LOGIC", 8));
        var update = body("Updated", 250, 5, 10, 20, Map.of("STEM", 9));
        ok("PUT", "/api/admin/products/" + id, admin, update, 200);
        JsonNode detail = ok("GET", "/api/products/" + id, null, null, 200);
        assertThat(detail.path("name").asString()).isEqualTo("Updated");
        assertThat(detail.path("price").asDouble()).isEqualTo(250);
        assertThat(detail.path("skillImpacts").size()).isEqualTo(1);
        assertThat(detail.path("skillImpacts").path(0).path("skillCode").asString()).isEqualTo("STEM");
        ok("PATCH", "/api/admin/products/" + id + "/stock", admin, Map.of("stockQuantity", 0), 200);
        assertThat(ok("GET", "/api/products/" + id, null, null, 200).path("stockQuantity").asInt()).isZero();
        ok("DELETE", "/api/admin/products/" + id, admin, null, 200);
        update.put("active", true);
        ok("PUT", "/api/admin/products/" + id, admin, update, 200);
        ok("GET", "/api/products/" + id, null, null, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negativePrice", "reversedAges", "negativeStock", "negativeImpact", "excessiveImpact"})
    void invalidProductCreateAndUpdateAreRejectedWithoutChangingProduct(String invalid) throws Exception {
        long id = product("Valid", 100, 3, 8, 10, Map.of("LOGIC", 8));
        var bad = body("Invalid", 100, 3, 8, 10, Map.of("LOGIC", 8));
        switch (invalid) {
            case "negativePrice" -> bad.put("price", -1);
            case "reversedAges" -> { bad.put("minAge", 10); bad.put("maxAge", 3); }
            case "negativeStock" -> bad.put("stockQuantity", -1);
            default -> bad.put("skillImpacts", List.of(Map.of("skillId", skills.get("LOGIC"),
                    "impactIndex", invalid.equals("negativeImpact") ? -1 : 11)));
        }
        expect(call("POST", "/api/admin/products", admin, bad), 400);
        expect(call("PUT", "/api/admin/products/" + id, admin, bad), 400);
        assertThat(ok("GET", "/api/products/" + id, null, null, 200).path("name").asString()).isEqualTo("Valid");
        assertThat(search("").path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void negativeStockPatchIsRejectedAndZeroIsAllowed() throws Exception {
        long id = product("Stock", 100, 3, 8, 10, Map.of());
        expect(call("PATCH", "/api/admin/products/" + id + "/stock", admin, Map.of("stockQuantity", -1)), 400);
        assertThat(ok("GET", "/api/products/" + id, null, null, 200).path("stockQuantity").asInt()).isEqualTo(10);
        ok("PATCH", "/api/admin/products/" + id + "/stock", admin, Map.of("stockQuantity", 0), 200);
    }

    @Test
    void categoryCrudAndDuplicateNamesAreEnforced() throws Exception {
        String name = "Unique " + UUID.randomUUID();
        long id = createCategory(name);
        expect(call("POST", "/api/admin/categories", admin, Map.of("name", name.toUpperCase())), 400);
        String renamed = "Renamed " + UUID.randomUUID();
        assertThat(ok("PUT", "/api/admin/categories/" + id, admin, Map.of("name", renamed), 200)
                .path("name").asString()).isEqualTo(renamed);
        long other = createCategory("Other " + UUID.randomUUID());
        expect(call("PUT", "/api/admin/categories/" + other, admin, Map.of("name", renamed)), 400);
        ok("DELETE", "/api/admin/categories/" + id, admin, null, 200);
        assertThat(ids(ok("GET", "/api/categories", null, null, 200), "id")).doesNotContain(id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void categoryWithActiveOrHiddenProductCannotBeDeleted(boolean hide) throws Exception {
        long id = product("Category guard", 100, 3, 8, 10, Map.of());
        if (hide) ok("DELETE", "/api/admin/products/" + id, admin, null, 200);
        expect(call("DELETE", "/api/admin/categories/" + category, admin, null), 400);
        assertThat(ids(ok("GET", "/api/categories", null, null, 200), "id")).contains(category);
    }

    @Test
    void skillCrudRejectsDuplicateCodeAndCodeChange() throws Exception {
        String code = skillCode();
        long id = ok("POST", "/api/admin/skills", admin, Map.of("code", code, "name", "New group"), 201)
                .path("id").asLong();
        expect(call("POST", "/api/admin/skills", admin, Map.of("code", code, "name", "Different name")), 400);
        expect(call("PUT", "/api/admin/skills/" + id, admin, Map.of("code", skillCode(), "name", "New name")), 400);
        assertThat(ok("PUT", "/api/admin/skills/" + id, admin, Map.of("code", code, "name", "Renamed group"), 200)
                .path("name").asString()).isEqualTo("Renamed group");
        ok("DELETE", "/api/admin/skills/" + id, admin, null, 200);
        assertThat(ids(ok("GET", "/api/skills", null, null, 200), "id")).doesNotContain(id);
    }

    @Test
    void duplicateSkillNameWithDifferentCodeIsAllowed() throws Exception {
        String name = "Same group " + UUID.randomUUID();
        ok("POST", "/api/admin/skills", admin, Map.of("code", skillCode(), "name", name), 201);
        ok("POST", "/api/admin/skills", admin, Map.of("code", skillCode(), "name", name), 201);
    }

    @Test
    void skillUsedByProductCannotBeDeleted() throws Exception {
        product("Using skill", 100, 3, 8, 10, Map.of("LOGIC", 8));
        expect(call("DELETE", "/api/admin/skills/" + skills.get("LOGIC"), admin, null), 400);
        assertThat(ids(ok("GET", "/api/skills", null, null, 200), "id")).contains(skills.get("LOGIC"));
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/admin/products", "POST,/api/admin/products", "PUT,/api/admin/products/1",
            "DELETE,/api/admin/products/1", "PATCH,/api/admin/products/1/stock",
            "POST,/api/admin/categories", "PUT,/api/admin/categories/1", "DELETE,/api/admin/categories/1",
            "POST,/api/admin/skills", "PUT,/api/admin/skills/1", "DELETE,/api/admin/skills/1",
            "GET,/api/admin/products/1/images", "POST,/api/admin/products/1/images",
            "DELETE,/api/admin/products/1/images/1", "PATCH,/api/admin/products/1/images/1/thumbnail",
            "PUT,/api/admin/products/1/images/order"})
    void customerAndAnonymousCannotUseAdminRoutes(String method, String path) throws Exception {
        expect(call(method, path, customer(), Map.of()), 403);
        Response anonymous = call(method, path, null, Map.of());
        assertThat(anonymous.status()).as("response: %s", anonymous.body()).isIn(401, 403);
    }

    @Test
    void assigningProductsScoresAllSkillsAndRemovingProductRestoresScores() throws Exception {
        String parent = customer();
        long child = child(parent);
        long first = product("First", 100, 0, 18, 10,
                Map.of("LOGIC", 8, "STEM", 6, "CREATIVE", 4, "PROBLEM_SOLVING", 2));
        assign(parent, child, first);
        JsonNode profile = profile(parent, child);
        assertThat(profile.path("totalProducts").asInt()).isEqualTo(1);
        assertScore(profile, "LOGIC", 4.0);
        assertScore(profile, "STEM", 3.0);
        assertScore(profile, "CREATIVE", 2.0);
        assertScore(profile, "PROBLEM_SOLVING", 1.0);
        long second = product("Second", 100, 0, 18, 10, Map.of("LOGIC", 2));
        long assignment = assign(parent, child, second);
        assertScore(profile(parent, child), "LOGIC", 4.6);
        ok("DELETE", "/api/children/" + child + "/products/" + assignment, parent, null, 200);
        assertScore(profile(parent, child), "LOGIC", 4.0);
    }

    @Test
    void editingProductImpactsRecalculatesExistingChildScores() throws Exception {
        String parent = customer();
        long child = child(parent);
        long id = product("Adjustable", 100, 0, 18, 10, Map.of("LOGIC", 8));
        assign(parent, child, id);
        assertScore(profile(parent, child), "LOGIC", 4);
        ok("PUT", "/api/admin/products/" + id, admin, body("Adjusted", 100, 0, 18, 10, Map.of("LOGIC", 3)), 200);
        assertScore(profile(parent, child), "LOGIC", 1.5);
    }

    @Test
    void singlePreviewPredictsGainWithoutMutatingProfileAndSkipsOwnedProduct() throws Exception {
        String parent = customer();
        long child = child(parent);
        long owned = product("Owned", 100, 0, 18, 10, Map.of("LOGIC", 10));
        long candidate = product("Candidate", 100, 0, 18, 10, Map.of("LOGIC", 2));
        assign(parent, child, owned);
        JsonNode preview = ok("GET", skillPath(child) + "/preview?productId=" + candidate, parent, null, 200);
        assertThat(preview.path("alreadyOwned").asBoolean()).isFalse();
        assertGain(preview.path("gains"), "LOGIC", 5, 5.5, 0.5);
        JsonNode skipped = ok("GET", skillPath(child) + "/preview?productId=" + owned, parent, null, 200);
        assertThat(skipped.path("alreadyOwned").asBoolean()).isTrue();
        assertThat(skipped.path("gains").size()).isZero();
        assertScore(profile(parent, child), "LOGIC", 5);
        assertThat(profile(parent, child).path("totalProducts").asInt()).isEqualTo(1);
    }

    @Test
    void bundlePreviewDeduplicatesIdsSkipsOwnedAndPredictsCombinedGain() throws Exception {
        String parent = customer();
        long child = child(parent);
        long owned = product("Owned", 100, 0, 18, 10, Map.of("LOGIC", 10));
        long first = product("Candidate one", 100, 0, 18, 10, Map.of("LOGIC", 2));
        long second = product("Candidate two", 100, 0, 18, 10, Map.of("LOGIC", 6, "STEM", 8));
        assign(parent, child, owned);
        JsonNode preview = ok("GET", skillPath(child) + "/preview-bundle?productIds="
                + owned + "," + first + "," + first + "," + second, parent, null, 200);
        assertThat(preview.path("alreadyOwnedProductIds").size()).isEqualTo(1);
        assertThat(preview.path("alreadyOwnedProductIds").path(0).asLong()).isEqualTo(owned);
        assertGain(preview.path("gains"), "LOGIC", 5, 6.9, 1.9);
        assertGain(preview.path("gains"), "STEM", 0, 4, 4);
        assertScore(profile(parent, child), "LOGIC", 5);
    }

    @Test
    void recommendationsPrioritizeWeakSkillAndExcludeOwnedWrongAgeHiddenAndSoldOut() throws Exception {
        String parent = customer();
        long child = child(parent);
        long owned = product("Owned", 100, 0, 18, 10,
                Map.of("LOGIC", 10, "STEM", 10, "CREATIVE", 10, "PROBLEM_SOLVING", 2));
        assign(parent, child, owned);
        long best = product("Weak skill support", 100, 0, 18, 10, Map.of("PROBLEM_SOLVING", 8));
        long lower = product("Less support", 100, 0, 18, 10, Map.of("PROBLEM_SOLVING", 3, "LOGIC", 10));
        long tooOld = product("Too old", 100, 15, 18, 10, Map.of("PROBLEM_SOLVING", 10));
        long soldOut = product("Sold out", 100, 0, 18, 0, Map.of("PROBLEM_SOLVING", 10));
        long hidden = product("Hidden", 100, 0, 18, 10, Map.of("PROBLEM_SOLVING", 10));
        ok("DELETE", "/api/admin/products/" + hidden, admin, null, 200);
        JsonNode result = ok("GET", skillPath(child) + "/recommendations?limit=20", parent, null, 200);
        assertThat(ids(result, "productId")).containsExactly(best, lower).doesNotContain(owned, tooOld, soldOut, hidden);
        assertGain(result.path(0).path("skillGains"), "PROBLEM_SOLVING", 1, 4.6, 3.6);
    }

    @Test
    void productMatchesUseChildAgeExcludeOwnedAndRankByProjectedGain() throws Exception {
        String parent = customer();
        long child = child(parent);
        long owned = product("Owned", 100, 0, 18, 10, Map.of("LOGIC", 10));
        assign(parent, child, owned);
        long strongest = product("Stem", 100, 0, 18, 10, Map.of("STEM", 8));
        long weaker = product("Logic", 100, 0, 18, 10, Map.of("LOGIC", 8));
        product("Wrong age", 100, 15, 18, 10, Map.of("STEM", 10));
        JsonNode result = ok("GET", "/api/children/" + child + "/product-matches?categoryId=" + category, parent, null, 200);
        List<Long> matched = new ArrayList<>();
        result.path("content").forEach(r -> matched.add(r.path("product").path("id").asLong()));
        assertThat(matched).containsExactly(strongest, weaker);
        assertThat(result.path("content").path(0).path("fitScore").asDouble()).isEqualTo(4);
        assertGain(result.path("content").path(0).path("gains"), "STEM", 0, 4, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/skill-profile", "/skill-profile/preview", "/skill-profile/preview-bundle",
            "/skill-profile/recommendations", "/skill-profile/timeline", "/product-matches"})
    void otherParentGets404ForSkillProfileAndMatches(String suffix) throws Exception {
        String parent = customer();
        long child = child(parent);
        long id = product("Private", 100, 0, 18, 10, Map.of("LOGIC", 8));
        assign(parent, child, id);
        String params = suffix.endsWith("/preview") ? "?productId=" + id
                : suffix.endsWith("/preview-bundle") ? "?productIds=" + id : "";
        expect(call("GET", "/api/children/" + child + suffix + params, customer(), null), 404);
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(JSON.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        // Each request observes persisted state rather than entities cached by a previous MockMvc call.
        em.flush();
        em.clear();
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        return new Response(response.getStatus(), content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content));
    }

    private JsonNode query(String path, String key, String value, String token) throws Exception {
        var request = MockMvcRequestBuilders.get(path).param(key, value);
        if (token != null) request.header("Authorization", "Bearer " + token);
        var raw = mvc.perform(request).andReturn().getResponse();
        em.flush();
        em.clear();
        Response response = new Response(raw.getStatus(), JSON.readTree(raw.getContentAsString(StandardCharsets.UTF_8)));
        expect(response, 200);
        return response.data();
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
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password), 200)
                .path("token").asString();
    }
    private String customer() throws Exception {
        String email = "catalog-" + UUID.randomUUID() + "@test.local";
        ok("POST", "/api/auth/register", null, Map.of("email", email, "password", "Passw0rd!", "fullName", "Parent"), 201);
        return login(email, "Passw0rd!");
    }
    private long createCategory(String name) throws Exception {
        return ok("POST", "/api/admin/categories", admin, Map.of("name", name), 201).path("id").asLong();
    }
    private String skillCode() {
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        StringBuilder code = new StringBuilder("TEST_");
        for (char c : hex.toCharArray()) code.append(Character.isDigit(c) ? (char) ('G' + c - '0') : c);
        return code.toString();
    }
    private Map<String, Object> body(String name, int price, int minAge, int maxAge, int stock, Map<String, Integer> impacts) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("price", price);
        body.put("minAge", minAge);
        body.put("maxAge", maxAge);
        body.put("stockQuantity", stock);
        body.put("categoryId", category);
        body.put("weightGrams", 500);
        body.put("lengthCm", 20);
        body.put("widthCm", 15);
        body.put("heightCm", 10);
        body.put("skillImpacts", impacts.entrySet().stream().map(e ->
                Map.of("skillId", skills.get(e.getKey()), "impactIndex", e.getValue())).toList());
        return body;
    }
    private long product(String name, int price, int minAge, int maxAge, int stock, Map<String, Integer> impacts) throws Exception {
        return ok("POST", "/api/admin/products", admin, body(name, price, minAge, maxAge, stock, impacts), 201)
                .path("id").asLong();
    }
    private JsonNode search(String query) throws Exception {
        return ok("GET", "/api/products?categoryId=" + category + (query.isEmpty() ? "" : "&" + query), null, null, 200);
    }
    private List<Long> ids(JsonNode rows, String key) {
        List<Long> ids = new ArrayList<>();
        rows.forEach(r -> { if (!r.path(key).isNull()) ids.add(r.path(key).asLong()); });
        return ids;
    }
    private JsonNode row(JsonNode rows, String key, long id) {
        for (JsonNode row : rows) if (row.path(key).asLong() == id) return row;
        throw new AssertionError("Missing " + key + "=" + id + " in " + rows);
    }
    private long child(String token) throws Exception {
        return ok("POST", "/api/children", token, Map.of("name", "Builder", "birthDate",
                LocalDate.now().minusYears(6).toString(), "gender", "MALE"), 201).path("id").asLong();
    }
    private long assign(String token, long child, long product) throws Exception {
        return ok("POST", "/api/children/" + child + "/products", token, Map.of("productId", product), 201).path("id").asLong();
    }
    private String skillPath(long child) { return "/api/children/" + child + "/skill-profile"; }
    private JsonNode profile(String token, long child) throws Exception { return ok("GET", skillPath(child), token, null, 200); }
    private void assertScore(JsonNode profile, String skill, double score) {
        assertThat(row(profile.path("skillScores"), "skillId", skills.get(skill)).path("score").asDouble()).isEqualTo(score);
    }
    private void assertGain(JsonNode gains, String skill, double current, double projected, double gain) {
        JsonNode r = row(gains, "skillId", skills.get(skill));
        assertThat(r.path("currentScore").asDouble()).isEqualTo(current);
        assertThat(r.path("projectedScore").asDouble()).isEqualTo(projected);
        assertThat(r.path("gain").asDouble()).isEqualTo(gain);
    }
}
