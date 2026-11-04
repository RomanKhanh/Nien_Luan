package com.brainblocks.backend.fulltest.chatbot;

import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.enums.SenderType;
import com.brainblocks.backend.exception.AiServiceException;
import com.brainblocks.backend.service.ai.GeminiService;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP coverage missing from regression/ChatbotApiTest; all generation and embedding is mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:chatbot-fulltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.chat.max-questions-per-minute=3",
        "app.knowledge.dir=target/chatbot-fulltest-knowledge",
        "app.product-embedding.backfill-on-startup=false",
        "app.skill-score.alpha=0.5",
        "GOOGLE_API_KEY=test-secret-must-never-reach-client"
})
@ActiveProfiles("test")
class ChatbotFullApiTest {
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PASSWORD = "Passw0rd!";
    private static final String SECRET = "test-secret-must-never-reach-client";

    @MockitoBean GeminiService gemini;
    @Autowired Environment environment;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    private TransactionTemplate tx;
    private String baseUrl;
    private String admin;
    private CustomerLogin owner;

    record CustomerLogin(long id, String token, String email) {}
    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }

    @BeforeEach
    void setUp() throws Exception {
        tx = new TransactionTemplate(manager);
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        admin = login("admin@test.local", "test-admin-password");
        // This class has its own H2 database. Clear conversation aggregates between cases.
        tx.executeWithoutResult(status -> {
            em.createQuery("select s from ChatSession s", ChatSession.class).getResultList().forEach(em::remove);
            em.createQuery("select d from KnowledgeDocument d", KnowledgeDocument.class).getResultList().forEach(em::remove);
            em.createQuery("update ChatbotConfig c set c.active = false").executeUpdate();
            Admin administrator = em.createQuery("select a from Admin a where a.email = :email", Admin.class)
                    .setParameter("email", "admin@test.local").getSingleResult();
            em.persist(ChatbotConfig.builder().admin(administrator).active(true).modelName("fake-model")
                    .temperature(0.2).maxTokens(1024).topK(4).build());
        });
        owner = customer();
        when(gemini.generateAnswer(any(), anyString())).thenReturn(answer(List.of()));
        when(gemini.embedQuery(anyString())).thenReturn(new float[]{1, 0});
        when(gemini.embedDocuments(anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).stream().map(ignored -> new float[]{1, 0}).toList());
    }

    @Test
    void childCanBeChangedAndDetachedButForeignChildCannotBeAttached() throws Exception {
        long first = child(owner.id(), "An");
        long second = child(owner.id(), "Binh");
        CustomerLogin outsider = customer();
        long foreign = child(outsider.id(), "Foreign");
        Response created = call("POST", "/api/chat/sessions", owner.token(), Map.of("childProfileId", first));
        assertThat(created.status()).isEqualTo(201);
        long id = created.data().path("id").asLong();
        assertThat(created.data().path("childProfileId").asLong()).isEqualTo(first);
        Response switched = call("PATCH", sessionPath(id), owner.token(), Map.of("childProfileId", second));
        assertThat(switched.status()).isEqualTo(200);
        assertThat(switched.data().path("childProfileId").asLong()).isEqualTo(second);
        assertThat(call("POST", "/api/chat/sessions", owner.token(), Map.of("childProfileId", foreign)).status()).isEqualTo(404);
        assertThat(call("PATCH", sessionPath(id), owner.token(), Map.of("childProfileId", foreign)).status()).isEqualTo(404);
        assertThat(call("GET", sessionPath(id), owner.token(), null).data().path("childProfileId").asLong()).isEqualTo(second);
        assertThat(call("GET", sessionPath(id), outsider.token(), null).status()).isEqualTo(404);
        assertThat(call("PATCH", sessionPath(id), outsider.token(), Map.of()).status()).isEqualTo(404);
        assertThat(call("DELETE", sessionPath(id), outsider.token(), null).status()).isEqualTo(404);
        Response detached = call("PATCH", sessionPath(id), owner.token(), Map.of());
        assertThat(detached.status()).isEqualTo(200);
        assertThat(detached.data().path("childProfileId").isNull()).isTrue();
        Response list = call("GET", "/api/chat/sessions", owner.token(), null);
        assertThat(list.data().valueStream().map(s -> s.path("id").asLong()).toList()).containsExactly(id);
        assertThat(call("DELETE", sessionPath(id), owner.token(), null).status()).isEqualTo(200);
        assertThat(call("GET", sessionPath(id), owner.token(), null).status()).isEqualTo(404);
        verifyNoInteractions(gemini);
    }

    @Test
    void suggestionsUseTheOwnedChildAndRejectForeignChild() throws Exception {
        long child = child(owner.id(), "ContextChild");
        long foreign = child(customer().id(), "PrivateChild");
        Response general = call("GET", "/api/chat/suggestions", owner.token(), null);
        Response specific = call("GET", "/api/chat/suggestions?childProfileId=" + child, owner.token(), null);
        assertThat(general.status()).isEqualTo(200);
        assertThat(general.data().size()).isEqualTo(3);
        assertThat(specific.status()).isEqualTo(200);
        assertThat(specific.data().size()).isEqualTo(4);
        assertThat(specific.data().get(0).asString()).contains("ContextChild", "7");
        assertThat(general.data().toString()).doesNotContain("ContextChild");
        assertThat(call("GET", "/api/chat/suggestions?childProfileId=" + foreign, owner.token(), null).status()).isEqualTo(404);
        verifyNoInteractions(gemini);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "blank", "long"})
    void invalidMessagesAreRejectedBeforeCallingAi(String kind) throws Exception {
        String content = switch (kind) { case "empty" -> ""; case "blank" -> " \n\t "; default -> "a".repeat(1001); };
        long id = session(null);
        assertThat(message(id, content).status()).isEqualTo(400);
        assertThat(call("GET", sessionPath(id), owner.token(), null).data().path("messages").isEmpty()).isTrue();
        verifyNoInteractions(gemini);
    }

    @Test
    void recommendationsRespectStockAgeAndOwnershipAndPromptContainsActualSkills() throws Exception {
        long child = child(owner.id(), "SkillChild");
        String keyword = "robot" + UUID.randomUUID().toString().replace("-", "");
        long valid = product(keyword + " eligible", true, 5, 7, 7);
        long hidden = product(keyword + " hidden", false, 5, 4, 10);
        long soldOut = product(keyword + " soldout", true, 0, 4, 10);
        long tooYoung = product(keyword + " young", true, 5, 1, 6);
        long tooOld = product(keyword + " old", true, 5, 8, 12);
        long owned = product(keyword + " owned", true, 5, 4, 10);
        tx.executeWithoutResult(status -> {
            Skill skill = Skill.builder().code("SKILL_" + UUID.randomUUID().toString().substring(0, 20)).name("Fixture Logic").build();
            em.persist(skill);
            em.persist(ProductSkillImpact.builder().product(em.getReference(Product.class, owned)).skill(skill).impactIndex(8).build());
            em.persist(ChildProduct.builder().childProfile(em.getReference(ChildProfile.class, child))
                    .product(em.getReference(Product.class, owned)).source(ProductSource.MANUAL).build());
        });
        when(gemini.generateAnswer(any(), anyString())).thenReturn(answer(
                List.of(valid, hidden, soldOut, tooYoung, tooOld, owned).stream()
                        .map(id -> new GeminiService.Suggestion(id, "AI proposed this ID")).toList()));
        long session = session(child);
        Response reply = message(session, keyword);
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.data().path("botMessage").path("suggestedProducts").valueStream()
                .map(p -> p.path("productId").asLong()).toList()).containsExactly(valid);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateAnswer(any(), prompt.capture());
        assertThat(prompt.getValue()).contains("SkillChild", "tuổi: 7", "Hồ sơ kỹ năng", "Fixture Logic", "Child note");
        String skillJson = prompt.getValue().lines().filter(line -> line.startsWith("Hồ sơ kỹ năng (JSON): "))
                .findFirst().orElseThrow().substring("Hồ sơ kỹ năng (JSON): ".length());
        JsonNode skillProfile = JSON.readTree(skillJson);
        assertThat(skillProfile.path("childProfileId").asLong()).isEqualTo(child);
        assertThat(skillProfile.path("totalProducts").asInt()).isEqualTo(1);
        JsonNode logic = skillProfile.path("skillScores").valueStream()
                .filter(score -> score.path("skillName").asString().equals("Fixture Logic")).findFirst().orElseThrow();
        assertThat(logic.path("score").asDouble()).isEqualTo(4.0);
        Response history = call("GET", sessionPath(session), owner.token(), null);
        assertThat(history.data().path("messages").size()).isEqualTo(2);
        assertThat(history.data().path("messages").get(1)).isEqualTo(reply.data().path("botMessage"));
    }

    @Test
    void aiFailureIsFriendlyAndDoesNotLeakSecretsOrStorePartialHistory() throws Exception {
        when(gemini.generateAnswer(any(), anyString())).thenThrow(new AiServiceException(SECRET));
        long session = session(null);
        Response failed = message(session, "Please advise");
        assertThat(failed.status()).isEqualTo(503);
        assertThat(failed.body().path("message").asString()).isEqualTo("AI service is not available right now, please try again later");
        assertThat(failed.body().toString()).doesNotContain(SECRET, "stackTrace", "AiServiceException", "com.brainblocks");
        assertThat(call("GET", sessionPath(session), owner.token(), null).data().path("messages").isEmpty()).isTrue();
    }

    @Test
    void burstLimitIsSharedAcrossSessionsAndDoesNotAffectAnotherCustomer() throws Exception {
        long first = session(null);
        long second = session(null);
        assertThat(message(first, "one").status()).isEqualTo(200);
        assertThat(message(second, "two").status()).isEqualTo(200);
        assertThat(message(first, "three").status()).isEqualTo(200);
        assertThat(message(second, "four").status()).isEqualTo(429);
        CustomerLogin another = customer();
        long otherSession = call("POST", "/api/chat/sessions", another.token(), null).data().path("id").asLong();
        assertThat(call("POST", sessionPath(otherSession) + "/messages", another.token(), Map.of("content", "hello")).status()).isEqualTo(200);
        verify(gemini, times(4)).generateAnswer(any(), anyString());
        assertThat(call("GET", sessionPath(second), owner.token(), null).data().path("messages").size()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TXT", "MD", "PDF", "DOCX"})
    void uploadsRealFormatsThroughHttpAndIndexesWithMockEmbeddings(String type) throws Exception {
        Response uploaded = upload("guide." + type.toLowerCase(), documentBytes(type));
        assertThat(uploaded.status()).isEqualTo(201);
        long id = uploaded.data().path("id").asLong();
        awaitIndexed(id);
        Response chunks = call("GET", "/api/admin/knowledge-documents/" + id + "/chunks", admin, null);
        assertThat(chunks.status()).isEqualTo(200);
        assertThat(chunks.data().get(0).path("content").asString()).contains("STEM education");
        assertThat(chunks.data().get(0).path("embedded").asBoolean()).isTrue();
        verify(gemini, atLeastOnce()).embedDocuments(anyList());
        assertThat(call("DELETE", "/api/admin/knowledge-documents/" + id, admin, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/admin/knowledge-documents/" + id + "/chunks", admin, null).status()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oversized.txt", "wrong.exe", "fake.pdf", "fake.docx", "binary.md"})
    void invalidUploadsReturn400WithoutEmbedding(String filename) throws Exception {
        byte[] bytes = filename.startsWith("oversized") ? new byte[10 * 1024 * 1024 + 1]
                : filename.startsWith("binary") ? new byte[]{0, 1, 2} : "plain text".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(filename, bytes).status()).isEqualTo(400);
        verifyNoInteractions(gemini);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "reindex", "delete", "file"})
    void processingDocumentsRejectConflictingOperations(String operation) throws Exception {
        long id = document(DocumentStatus.PROCESSING, "processing", "pending", false);
        String path = "/api/admin/knowledge-documents/" + id;
        Response result = switch (operation) {
            case "status" -> call("PATCH", path + "/status", admin, Map.of("status", "DISABLED"));
            case "reindex" -> call("POST", path + "/reindex", admin, null);
            case "delete" -> call("DELETE", path, admin, null);
            default -> multipart(path + "/file", "replacement.txt", documentBytes("TXT"));
        };
        assertThat(result.status()).isEqualTo(409);
        DocumentStatus actual = tx.execute(status -> em.find(KnowledgeDocument.class, id).getStatus());
        assertThat(actual).isEqualTo(DocumentStatus.PROCESSING);
        verifyNoInteractions(gemini);
    }

    @Test
    void disabledDocumentIsExcludedFromChatPromptAndSourcesThenCanBeEnabled() throws Exception {
        long indexed = document(DocumentStatus.INDEXED, "visible", "VISIBLE_RAG_TEXT", true);
        long disabled = document(DocumentStatus.DISABLED, "private", "DISABLED_RAG_TEXT", true);
        long session = session(null);
        Response first = message(session, "STEM knowledge");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.data().path("botMessage").path("sources").valueStream()
                .map(s -> s.path("documentId").asLong()).toList()).containsExactly(indexed);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateAnswer(any(), prompt.capture());
        assertThat(prompt.getValue()).contains("VISIBLE_RAG_TEXT").doesNotContain("DISABLED_RAG_TEXT");
        Response enabled = call("PATCH", "/api/admin/knowledge-documents/" + disabled + "/status", admin, Map.of("status", "INDEXED"));
        assertThat(enabled.status()).isEqualTo(200);
        assertThat(enabled.data().path("status").asString()).isEqualTo("INDEXED");
        Response second = message(session, "More STEM knowledge");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.data().path("botMessage").path("sources").valueStream()
                .map(s -> s.path("documentId").asLong()).toList()).containsExactlyInAnyOrder(indexed, disabled);
        assertThat(call("PATCH", "/api/admin/knowledge-documents/" + disabled + "/status", admin,
                Map.of("status", "DISABLED")).status()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({"temperature,-0.01", "temperature,1.01", "maxTokens,1023", "maxTokens,8193", "topK,0", "topK,21"})
    void configurationBoundsAreEnforcedOnCreateAndUpdate(String field, double value) throws Exception {
        long id = call("POST", "/api/admin/chatbot-configs", admin, config()).data().path("id").asLong();
        Map<String, Object> invalid = new java.util.HashMap<>(config());
        invalid.put(field, field.equals("temperature") ? (Object) value : (int) value);
        assertThat(call("POST", "/api/admin/chatbot-configs", admin, invalid).status()).isEqualTo(400);
        assertThat(call("PUT", "/api/admin/chatbot-configs/" + id, admin, invalid).status()).isEqualTo(400);
    }

    @Test
    void updatingActiveConfigurationChangesSettingsPassedToAiWithoutExposingKey() throws Exception {
        Response created = call("POST", "/api/admin/chatbot-configs", admin, config());
        assertThat(created.status()).isEqualTo(201);
        long id = created.data().path("id").asLong();
        assertThat(call("PATCH", "/api/admin/chatbot-configs/" + id + "/activate", admin, null).status()).isEqualTo(200);
        Map<String, Object> updated = Map.of("modelName", "fake-updated", "temperature", 1.0,
                "maxTokens", 8192, "topK", 20, "systemPrompt", "Updated instructions");
        Response response = call("PUT", "/api/admin/chatbot-configs/" + id, admin, updated);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().path("active").asBoolean()).isTrue();
        assertThat(message(session(null), "hello").status()).isEqualTo(200);
        verify(gemini).generateAnswer(argThat(settings -> settings.modelName().equals("fake-updated")
                && settings.temperature() == 1.0 && settings.maxTokens() == 8192 && settings.topK() == 20
                && settings.systemPrompt().equals("Updated instructions")), anyString());
        Response list = call("GET", "/api/admin/chatbot-configs", admin, null);
        assertThat(list.data().valueStream().filter(c -> c.path("active").asBoolean()).count()).isEqualTo(1);
        for (Response r : List.of(created, response, list)) {
            assertThat(r.body().toString().toLowerCase()).doesNotContain("apikey", "api_key", SECRET);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/knowledge-documents", "/api/admin/chatbot-configs", "/api/admin/chat/sessions"})
    void customersCannotAccessAdminChatbotApis(String path) throws Exception {
        assertThat(call("GET", path, owner.token(), null).status()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "DELETE", "MESSAGE"})
    void unauthenticatedChatRequestsAreBlocked(String operation) throws Exception {
        long id = session(null);
        Response result = switch (operation) {
            case "POST" -> call("POST", "/api/chat/sessions", null, null);
            case "DELETE" -> call("DELETE", sessionPath(id), null, null);
            case "MESSAGE" -> call("POST", sessionPath(id) + "/messages", null, Map.of("content", "hello"));
            default -> call("GET", "/api/chat/sessions", null, null);
        };
        assertThat(result.status()).isIn(401, 403);
        verifyNoInteractions(gemini);
    }

    @Test
    void adminMonitoringAndDashboardReturnExactSeededConversationAndOrderCounts() throws Exception {
        JsonNode baseline = call("GET", "/api/admin/stats", admin, null).data();
        CustomerLogin second = customer();
        long[] sessions = tx.execute(status -> {
            ChatSession a = ChatSession.builder().customer(em.getReference(Customer.class, owner.id())).title("Monitor fixture").build();
            a.addMessage(ChatMessage.builder().sender(SenderType.USER).content("Repeat question").build());
            a.addMessage(ChatMessage.builder().sender(SenderType.BOT).content("First answer").topic("Fixture Topic").responseTimeMs(100).build());
            a.addMessage(ChatMessage.builder().sender(SenderType.USER).content("Repeat question").build());
            a.addMessage(ChatMessage.builder().sender(SenderType.BOT).content("Second answer").topic("Fixture Topic").responseTimeMs(300).build());
            ChatSession b = ChatSession.builder().customer(em.getReference(Customer.class, second.id())).title("Other fixture").build();
            b.addMessage(ChatMessage.builder().sender(SenderType.USER).content("Different question").build());
            b.addMessage(ChatMessage.builder().sender(SenderType.BOT).content("Third answer").topic("Other Topic").responseTimeMs(200).build());
            em.persist(a); em.persist(b);
            for (OrderStatus orderStatus : List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)) {
                em.persist(Order.builder().customer(em.getReference(Customer.class, owner.id()))
                        .orderCode("CHAT-" + UUID.randomUUID().toString().substring(0, 20))
                        .receiverName("Fixture").receiverPhone("0901234567").shippingAddress("Fixture address")
                        .subtotal(new BigDecimal("100000")).shippingFee(new BigDecimal("20000"))
                        .totalAmount(new BigDecimal("120000")).status(orderStatus).paymentMethod(PaymentMethod.COD).build());
            }
            return new long[]{a.getId(), b.getId()};
        });
        Response stats = call("GET", "/api/admin/chat/stats", admin, null);
        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.data().path("totalSessions").asLong()).isEqualTo(2);
        assertThat(stats.data().path("totalQuestions").asLong()).isEqualTo(3);
        assertThat(stats.data().path("questionsLast7Days").asLong()).isEqualTo(3);
        assertThat(stats.data().path("avgResponseTimeMs").asDouble()).isEqualTo(200);
        assertThat(stats.data().path("topTopics").get(0).path("topic").asString()).isEqualTo("Fixture Topic");
        assertThat(stats.data().path("topTopics").get(0).path("total").asLong()).isEqualTo(2);
        assertThat(stats.data().path("topQuestions").get(0).path("total").asLong()).isEqualTo(2);
        assertThat(stats.data().path("dailyQuestions").size()).isEqualTo(14);
        assertThat(stats.data().path("dailyQuestions").valueStream().mapToLong(d -> d.path("total").asLong()).sum()).isEqualTo(3);
        Response monitored = call("GET", "/api/admin/chat/sessions/" + sessions[0], admin, null);
        assertThat(monitored.status()).isEqualTo(200);
        assertThat(monitored.data().path("customerEmail").asString()).isEqualTo(owner.email());
        assertThat(monitored.data().path("messages").size()).isEqualTo(4);
        Response found = call("GET", "/api/admin/chat/sessions?keyword=" + owner.email(), admin, null);
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.data().path("content").valueStream().map(s -> s.path("id").asLong()).toList()).containsExactly(sessions[0]);
        JsonNode dashboard = call("GET", "/api/admin/stats", admin, null).data();
        assertThat(dashboard.path("totalCustomers").asLong()).isEqualTo(baseline.path("totalCustomers").asLong() + 1);
        assertThat(dashboard.path("totalOrders").asLong()).isEqualTo(baseline.path("totalOrders").asLong() + 2);
        assertThat(dashboard.path("ordersByStatus").path("CANCELLED").asLong()).isEqualTo(baseline.path("ordersByStatus").path("CANCELLED").asLong() + 1);
        assertThat(dashboard.path("deliveredRevenue").decimalValue()).isEqualByComparingTo(baseline.path("deliveredRevenue").decimalValue().add(new BigDecimal("100000")));
        assertThat(dashboard.path("chatQuestions").asLong()).isEqualTo(3);
        assertThat(dashboard.path("chatTopics").get(0).path("total").asLong()).isEqualTo(2);
        verifyNoInteractions(gemini);
    }

    private GeminiService.Answer answer(List<GeminiService.Suggestion> products) {
        return new GeminiService.Answer("Mock STEM answer", GeminiService.OTHER_TOPIC, products);
    }

    private Map<String, Object> config() {
        return Map.of("modelName", "fake-model", "temperature", 0.0, "maxTokens", 1024, "topK", 1);
    }

    private String sessionPath(long id) { return "/api/chat/sessions/" + id; }

    private long session(Long child) throws Exception {
        Response result = call("POST", "/api/chat/sessions", owner.token(), child == null ? null : Map.of("childProfileId", child));
        assertThat(result.status()).isEqualTo(201);
        return result.data().path("id").asLong();
    }

    private Response message(long session, String content) throws Exception {
        return call("POST", sessionPath(session) + "/messages", owner.token(), Map.of("content", content));
    }

    private long child(long customer, String name) {
        return tx.execute(status -> {
            ChildProfile child = ChildProfile.builder().customer(em.getReference(Customer.class, customer))
                    .name(name).birthDate(LocalDate.now().minusYears(7)).note("Child note").build();
            em.persist(child);
            return child.getId();
        });
    }

    private long product(String name, boolean active, int stock, int minAge, int maxAge) {
        return tx.execute(status -> {
            Category category = Category.builder().name("Chat fixture " + UUID.randomUUID()).build();
            em.persist(category);
            Product product = Product.builder().name(name).description(name).category(category).active(active)
                    .stockQuantity(stock).minAge(minAge).maxAge(maxAge).price(BigDecimal.TEN)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            em.persist(product);
            return product.getId();
        });
    }

    private long document(DocumentStatus state, String title, String text, boolean embedded) {
        return tx.execute(status -> {
            Admin administrator = em.createQuery("select a from Admin a where a.email = :email", Admin.class)
                    .setParameter("email", "admin@test.local").getSingleResult();
            KnowledgeDocument doc = KnowledgeDocument.builder().admin(administrator).title(title)
                    .fileUrl("fixture-" + UUID.randomUUID() + ".txt").fileType("TXT").status(state).build();
            if (embedded) doc.getChunks().add(DocumentChunk.builder().knowledgeDocument(doc).chunkIndex(0)
                    .content(text).embedding(new float[]{1, 0}).build());
            em.persist(doc);
            return doc.getId();
        });
    }

    private CustomerLogin customer() throws Exception {
        String email = "fullchat-" + UUID.randomUUID() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null, Map.of("fullName", "Chat Fixture", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        long id = tx.execute(status -> em.createQuery("select c.id from Customer c where c.email = :email", Long.class)
                .setParameter("email", email).getSingleResult());
        return new CustomerLogin(id, login(email, PASSWORD), email);
    }

    private String login(String email, String password) throws Exception {
        Response result = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(result.status()).isEqualTo(200);
        return result.data().path("token").asString();
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.build());
    }

    private Response upload(String filename, byte[] bytes) throws Exception {
        return multipart("/api/admin/knowledge-documents", filename, bytes);
    }

    private Response multipart(String path, String filename, byte[] bytes) throws Exception {
        String boundary = "----fullchat" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nChat fixture\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(java.time.Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + admin)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
    }

    private Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).as("responses must never expose the server AI key").doesNotContain(SECRET);
        return new Response(response.statusCode(), response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body()));
    }

    private void awaitIndexed(long id) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            DocumentStatus state = tx.execute(status -> em.find(KnowledgeDocument.class, id).getStatus());
            if (state == DocumentStatus.INDEXED) return;
            assertThat(state).as("indexing failed for document %s", id).isEqualTo(DocumentStatus.PROCESSING);
            Thread.sleep(50);
        }
        throw new AssertionError("Document " + id + " did not become INDEXED within 10 seconds");
    }

    private byte[] documentBytes(String type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (type.equals("PDF")) {
            try (PDDocument pdf = new PDDocument()) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.showText("STEM education");
                    stream.endText();
                }
                pdf.save(bytes);
            }
        } else if (type.equals("DOCX")) {
            try (XWPFDocument doc = new XWPFDocument()) {
                doc.createParagraph().createRun().setText("STEM education");
                doc.write(bytes);
            }
        } else {
            bytes.write("STEM education".getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
