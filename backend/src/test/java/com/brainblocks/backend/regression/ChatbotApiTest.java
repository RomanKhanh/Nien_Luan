package com.brainblocks.backend.regression;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.service.ai.GeminiService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Chatbot & cơ sở tri thức qua HTTP thật (RANDOM_PORT, H2): đường dẫn, phân quyền, mã lỗi và hình dạng JSON
 * khớp docs/chatbot-api.md của frontend. Gemini được giả lập, không gửi dữ liệu đến Google.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatbotApiTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @MockitoBean
    private GeminiService gemini;
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
        when(gemini.embedDocuments(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).stream()
                .map(text -> new float[768]).map(ChatbotApiTest::unit).toList());
        when(gemini.embedQuery(anyString())).thenReturn(unit(new float[768]));
    }

    @Test
    void adminConfiguresChatbotAndManagesKnowledgeDocuments() throws Exception {
        // maxTokens quá nhỏ bị chặn ở backend, không chỉ ở form frontend
        Map<String, Object> config = Map.of("modelName", "gemini-flash-latest", "temperature", 0.3,
                "maxTokens", 2048, "topK", 4, "systemPrompt", "Trả lời ngắn gọn");
        assertThat(call("POST", "/api/admin/chatbot-configs", adminToken,
                Map.of("modelName", "m", "temperature", 0.3, "maxTokens", 64, "topK", 4)).status()).isEqualTo(400);
        Res created = call("POST", "/api/admin/chatbot-configs", adminToken, config);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.data().path("active").asBoolean()).isFalse();
        long configId = created.data().path("id").asLong();
        assertThat(call("PATCH", "/api/admin/chatbot-configs/" + configId + "/activate", adminToken, null)
                .data().path("active").asBoolean()).isTrue();
        Res configs = call("GET", "/api/admin/chatbot-configs", adminToken, null);
        assertThat(configs.data().valueStream().filter(c -> c.path("active").asBoolean()).count()).isEqualTo(1);
        assertThat(configs.body().toString()).doesNotContain("apiKey");

        String title = "Hướng dẫn STEM " + UUID.randomUUID();
        Res uploaded = upload(title, "stem.txt", "Đồ chơi lắp ráp giúp bé phát triển tư duy logic.");
        assertThat(uploaded.status()).isEqualTo(201);
        long docId = uploaded.data().path("id").asLong();
        JsonNode doc = waitForStatus(docId, title, "INDEXED");
        assertThat(doc.path("chunkCount").asLong()).isEqualTo(1);
        assertThat(doc.path("fileType").asString()).isEqualTo("TXT");
        Res chunks = call("GET", "/api/admin/knowledge-documents/" + docId + "/chunks", adminToken, null);
        assertThat(chunks.data().get(0).path("embedded").asBoolean()).isTrue();
        assertThat(chunks.data().get(0).has("embedding")).isFalse();

        assertThat(upload("Sai loại", "virus.exe", "x").status()).isEqualTo(400);

        // thay file: giữ tài liệu, đoạn cũ bị thay bằng nội dung file mới; file sai loại bị từ chối
        assertThat(replaceFile(docId, "virus.exe", "x").status()).isEqualTo(400);
        Res replaced = replaceFile(docId, "stem-v2.md", "Bộ xếp hình giúp bé sáng tạo.");
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(replaced.data().path("status").asString()).isEqualTo("PROCESSING");
        assertThat(replaced.data().path("chunkCount").asLong()).isZero();
        assertThat(waitForStatus(docId, title, "INDEXED").path("fileType").asString()).isEqualTo("MD");
        assertThat(call("GET", "/api/admin/knowledge-documents/" + docId + "/chunks", adminToken, null)
                .data().get(0).path("content").asString()).isEqualTo("Bộ xếp hình giúp bé sáng tạo.");

        assertThat(call("GET", "/api/admin/knowledge-documents?status=WRONG", adminToken, null).status()).isEqualTo(400);
        assertThat(call("PATCH", "/api/admin/knowledge-documents/" + docId + "/status", adminToken,
                Map.of("status", "UPLOADED")).status()).isEqualTo(400);
        assertThat(call("PATCH", "/api/admin/knowledge-documents/" + docId + "/status", adminToken,
                Map.of("status", "DISABLED")).data().path("status").asString()).isEqualTo("DISABLED");
        assertThat(call("PUT", "/api/admin/knowledge-documents/" + docId, adminToken, Map.of("title", "Đổi tên"))
                .data().path("title").asString()).isEqualTo("Đổi tên");
        assertThat(call("POST", "/api/admin/knowledge-documents/" + docId + "/reindex", adminToken, null)
                .data().path("status").asString()).isEqualTo("PROCESSING");
        waitForStatus(docId, "Đổi tên", "INDEXED");
        assertThat(call("DELETE", "/api/admin/knowledge-documents/" + docId, adminToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/admin/knowledge-documents/" + docId + "/chunks", adminToken, null).status()).isEqualTo(404);
    }

    @Test
    void customerChatsAndAdminMonitorsConversation() throws Exception {
        long productId = createProduct();
        String customer = newCustomer();
        when(gemini.generateAnswer(any(), anyString())).thenReturn(new GeminiService.Answer(
                "Bé có thể thử **robot lập trình**.", "Gợi ý theo kỹ năng",
                List.of(new GeminiService.Suggestion(productId, "Rèn tư duy logic"))));

        Res session = call("POST", "/api/chat/sessions", customer, null);
        assertThat(session.status()).isEqualTo(201);
        long sessionId = session.data().path("id").asLong();
        assertThat(session.data().path("messages").isEmpty()).isTrue();

        assertThat(call("POST", "/api/chat/sessions/" + sessionId + "/messages", customer, Map.of("content", " "))
                .status()).isEqualTo(400);
        Res reply = call("POST", "/api/chat/sessions/" + sessionId + "/messages", customer,
                Map.of("content", "Robot lập trình nào hợp với bé 7 tuổi?"));
        assertThat(reply.status()).isEqualTo(200);
        JsonNode bot = reply.data().path("botMessage");
        assertThat(reply.data().path("userMessage").path("sender").asString()).isEqualTo("USER");
        assertThat(bot.path("topic").asString()).isEqualTo("Gợi ý theo kỹ năng");
        assertThat(bot.path("suggestedProducts").get(0).path("productId").asLong()).isEqualTo(productId);
        assertThat(bot.path("suggestedProducts").get(0).path("reason").asString()).isEqualTo("Rèn tư duy logic");

        Res sessions = call("GET", "/api/chat/sessions", customer, null);
        assertThat(sessions.data().get(0).path("title").asString()).isEqualTo("Robot lập trình nào hợp với bé 7 tuổi?");
        assertThat(sessions.data().get(0).path("messageCount").asLong()).isEqualTo(2);
        assertThat(call("GET", "/api/chat/sessions/" + sessionId, customer, null).data().path("messages").size()).isEqualTo(2);
        assertThat(call("PATCH", "/api/chat/sessions/" + sessionId, customer, Map.of()).status()).isEqualTo(200);
        assertThat(call("GET", "/api/chat/suggestions", customer, null).data().size()).isBetween(3, 4);

        // khách khác không thấy hội thoại; khách không vào được API quản trị và ngược lại
        String outsider = newCustomer();
        assertThat(call("GET", "/api/chat/sessions/" + sessionId, outsider, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/admin/chat/stats", customer, null).status()).isEqualTo(403);
        assertThat(call("GET", "/api/chat/sessions", adminToken, null).status()).isEqualTo(403);
        assertThat(call("GET", "/api/chat/sessions", null, null).status()).isEqualTo(401);

        Res stats = call("GET", "/api/admin/chat/stats", adminToken, null);
        assertThat(stats.data().path("totalQuestions").asLong()).isPositive();
        assertThat(stats.data().path("dailyQuestions").size()).isEqualTo(14);
        Res found = call("GET", "/api/admin/chat/sessions?keyword=robot%20l%E1%BA%ADp%20tr%C3%ACnh&page=0&size=10",
                adminToken, null);
        assertThat(found.data().path("content").valueStream().map(s -> s.path("id").asLong()).toList()).contains(sessionId);
        Res detail = call("GET", "/api/admin/chat/sessions/" + sessionId, adminToken, null);
        assertThat(detail.data().path("customerEmail").asString()).endsWith("@test.local");
        assertThat(detail.data().path("messages").size()).isEqualTo(2);

        assertThat(call("DELETE", "/api/chat/sessions/" + sessionId, customer, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/chat/sessions/" + sessionId, customer, null).status()).isEqualTo(404);
    }

    private JsonNode waitForStatus(long docId, String title, String status) throws Exception {
        for (int i = 0; i < 100; i++) {
            Res list = call("GET", "/api/admin/knowledge-documents?keyword="
                    + java.net.URLEncoder.encode(title, StandardCharsets.UTF_8) + "&size=100", adminToken, null);
            for (JsonNode doc : list.data().path("content")) {
                if (doc.path("id").asLong() == docId && doc.path("status").asString().equals(status)) return doc;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document " + docId + " did not reach " + status);
    }

    private static float[] unit(float[] vector) {
        vector[0] = 1;
        return vector;
    }

    private Res upload(String title, String fileName, String content) throws Exception {
        return multipart("/api/admin/knowledge-documents", title, fileName, content);
    }

    private Res replaceFile(long docId, String fileName, String content) throws Exception {
        return multipart("/api/admin/knowledge-documents/" + docId + "/file", null, fileName, content);
    }

    // title null = chỉ gửi field file
    private Res multipart(String path, String title, String fileName, String content) throws Exception {
        String boundary = "----chatbot" + UUID.randomUUID();
        String body = (title == null ? "" : "--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n" + title + "\r\n")
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/plain\r\n\r\n" + content + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> r = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new Res(r.statusCode(), JSON.readTree(r.body()));
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
        String email = "chat-" + UUID.randomUUID() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Chat User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        return login(email, PASSWORD);
    }

    private long createProduct() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Category category = Category.builder().name("chat-" + UUID.randomUUID()).build();
            em.persist(category);
            Product product = Product.builder().name("[CHAT] Robot lập trình").description("Robot giúp bé học lập trình")
                    .price(new BigDecimal("350000.00")).stockQuantity(5).minAge(5).maxAge(12).category(category)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            em.persist(product);
            return product.getId();
        });
    }
}
