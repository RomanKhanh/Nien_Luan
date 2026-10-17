package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.dto.request.chat.*;
import com.brainblocks.backend.dto.request.knowledge.*;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.exception.*;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.security.CustomUserDetails;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.skill.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// H2 + AI giả lập: kiểm tra nghiệp vụ thật, không dùng key hoặc gửi dữ liệu đến Google.
@DataJpaTest(showSql = false, properties = "app.knowledge.dir=target/test-knowledge")
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatbotConfigService.class, KnowledgeBaseService.class, KnowledgeDocumentStorage.class,
        ChatService.class, ChatRateLimiter.class, CurrentUserProvider.class, ChildProfileAccessGuard.class,
        SkillProfileService.class, SkillRecommendationService.class, SkillScoreCalculator.class,
        ChatbotServicesTest.SecurityAndValidation.class})
class ChatbotServicesTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityAndValidation {
        @Bean static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }
    }

    @MockitoBean GeminiService gemini;
    @MockitoBean KnowledgeIndexingWorker queuedWorker;
    @Autowired ChatbotConfigService configService;
    @Autowired KnowledgeBaseService knowledge;
    @Autowired ChatService chat;
    @Autowired KnowledgeDocumentStorage storage;
    @Autowired ChatbotConfigRepository configs;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    TransactionTemplate tx;
    Admin admin;
    Customer customer;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            sessions.deleteAll();
            documents.deleteAll();
            configs.deleteAll();
            em.flush();
            String suffix = java.util.UUID.randomUUID().toString();
            admin = Admin.builder().fullName("Admin AI").email(suffix + "@admin.local").password("test").build();
            customer = Customer.builder().fullName("Phụ huynh AI").email(suffix + "@customer.local").password("test").build();
            em.persist(admin); em.persist(customer);
        });
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void configurationValidationAuthorizationAndSingleActiveConfig() {
        login(admin);
        assertThatThrownBy(() -> configService.createConfig(new ChatbotConfigRequest(" ", 2.0, 1, 0, null)))
                .isInstanceOf(ConstraintViolationException.class);
        var first = configService.createConfig(configRequest());
        var second = configService.createConfig(configRequest());
        assertThat(first.active()).isFalse();
        configService.activateConfig(first.id());
        configService.activateConfig(second.id());
        assertThat(configService.getConfigs().stream().filter(c -> c.active()).map(c -> c.id()).toList())
                .containsExactly(second.id());
        configService.updateConfig(second.id(), new ChatbotConfigRequest("model-test", 0.1, 2048, 2, "Tiếng Việt"));
        assertThat(configService.getActiveSettings().modelName()).isEqualTo("model-test");
        login(customer);
        assertThatThrownBy(() -> configService.getConfigs()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void sessionAndChildOwnershipAreEnforcedAndInputIsValidated() {
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        assertThat(session.messages()).isEmpty();
        assertThatThrownBy(() -> chat.sendMessage(session.id(), new SendChatMessageRequest(" ")))
                .isInstanceOf(ConstraintViolationException.class);
        Customer outsider = tx.execute(status -> {
            Customer c = Customer.builder().fullName("Khác").email(java.util.UUID.randomUUID() + "@other.local").password("test").build();
            em.persist(c); return c;
        });
        login(outsider);
        assertThatThrownBy(() -> chat.getSession(session.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chat.updateSession(session.id(), new ChatSessionRequest(null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chat.deleteSession(session.id())).isInstanceOf(ResourceNotFoundException.class);
        Long otherChild = tx.execute(status -> {
            ChildProfile child = ChildProfile.builder().customer(outsider).name("Bé khác").birthDate(LocalDate.of(2020, 1, 1)).build();
            em.persist(child); return child.getId();
        });
        login(customer);
        assertThatThrownBy(() -> chat.createSession(new ChatSessionRequest(otherChild))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(gemini);
    }

    @Test
    void replyFiltersInventedProductIdsAndPreservesReasonsAndSourcesInHistory() {
        activateConfig();
        Long productId = product();
        indexDocument();
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        when(gemini.embedQuery(anyString())).thenReturn(new float[]{1, 0});
        when(gemini.generateAnswer(any(), anyString())).thenReturn(new GeminiService.Answer("Đồ chơi hỗ trợ logic.", "Logic",
                List.of(new GeminiService.Suggestion(productId, "Phù hợp tuổi"),
                        new GeminiService.Suggestion(-1L, "ID giả"))));
        var reply = chat.sendMessage(session.id(), new SendChatMessageRequest("Gợi ý robot cho bé 6 tuổi"));
        assertThat(reply.userMessage().sender()).isEqualTo("USER");
        assertThat(reply.userMessage().suggestedProducts()).isEmpty();
        assertThat(reply.botMessage().suggestedProducts()).extracting(p -> p.productId()).containsExactly(productId);
        assertThat(reply.botMessage().sources()).hasSize(1);
        var history = chat.getSession(session.id());
        assertThat(history.title()).isEqualTo("Gợi ý robot cho bé 6 tuổi");
        assertThat(history.messageCount()).isEqualTo(2);
        assertThat(history.messages().getLast().sources()).isEqualTo(reply.botMessage().sources());
        assertThat(history.messages().getLast().suggestedProducts().getFirst().reason()).isEqualTo("Phù hợp tuổi");
        // Topic ngoài danh sách cố định được quy về "Khác" để thống kê gom nhóm được.
        assertThat(reply.botMessage().topic()).isEqualTo(GeminiService.OTHER_TOPIC);
        login(admin);
        assertThat(chat.getSessionsForAdmin("hỗ trợ logic", 0, 10).content()).hasSize(1);
        assertThat(chat.getSessionsForAdmin("không có", 0, 10).content()).isEmpty();
        assertThat(chat.getStats().totalQuestions()).isEqualTo(1);
        assertThat(chat.getStats().topSuggestedProducts().getFirst().productId()).isEqualTo(productId);
        assertThat(chat.getStats().dailyQuestions()).hasSize(14);
    }

    @Test
    void failedAiCallDoesNotLeaveHalfOfAConversation() {
        activateConfig();
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        when(gemini.generateAnswer(any(), anyString())).thenThrow(new AiServiceException("test failure"));
        assertThatThrownBy(() -> chat.sendMessage(session.id(), new SendChatMessageRequest("Xin chào")))
                .isInstanceOf(AiServiceException.class);
        assertThat(chat.getSession(session.id()).messages()).isEmpty();
        assertThat(chat.getSession(session.id()).title()).isNull();
    }

    @Test
    void selectedChildAddsRealSkillContextAndHiddenProductsAreRecheckedAfterAi() {
        activateConfig();
        Long productId = product();
        login(customer);
        Long childId = tx.execute(status -> {
            ChildProfile child = ChildProfile.builder().customer(customer).name("Bé An")
                    .birthDate(LocalDate.now().minusYears(6)).note("Thích robot").build();
            em.persist(child); return child.getId();
        });
        var session = chat.createSession(new ChatSessionRequest(childId));
        when(gemini.generateAnswer(any(), anyString())).thenAnswer(invocation -> {
            String context = invocation.getArgument(1);
            // Mô tả sản phẩm có trong ngữ cảnh để AI giải thích được lợi ích giáo dục.
            assertThat(context).contains("Bé An", "tuổi: 6", "Hồ sơ kỹ năng", "Thích robot", "Logic sáng tạo");
            tx.executeWithoutResult(status -> em.find(Product.class, productId).setActive(false));
            return new GeminiService.Answer("Tôi có thể tư vấn thêm.", "Gợi ý theo kỹ năng",
                    List.of(new GeminiService.Suggestion(productId, "Robot")));
        });
        var bot = chat.sendMessage(session.id(), new SendChatMessageRequest("Gợi ý robot")).botMessage();
        assertThat(bot.suggestedProducts()).isEmpty();
        assertThat(bot.topic()).isEqualTo("Gợi ý theo kỹ năng");
    }

    @Test
    void irrelevantKnowledgeIsSkippedAndRetrievalFailureStillAnswers() {
        activateConfig();
        indexDocument();
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        when(gemini.generateAnswer(any(), anyString())).thenReturn(new GeminiService.Answer("Xin chào!", "Chào hỏi", List.of()));
        // Vector vuông góc với vector tài liệu: cosine 0 dưới ngưỡng, không trả "nguồn" lạc đề.
        when(gemini.embedQuery(anyString())).thenReturn(new float[]{0, 1});
        assertThat(chat.sendMessage(session.id(), new SendChatMessageRequest("Xin chào")).botMessage().sources()).isEmpty();
        when(gemini.embedQuery(anyString())).thenThrow(new AiServiceException("quota"));
        var reply = chat.sendMessage(session.id(), new SendChatMessageRequest("Đồ chơi logic"));
        assertThat(reply.botMessage().content()).isEqualTo("Xin chào!");
        assertThat(reply.botMessage().sources()).isEmpty();
    }

    @Test
    void longSourcesAreShortenedToSnippets() {
        var chunk = new KnowledgeBaseService.RetrievedChunk(1L, 2L, "Tài liệu", "a".repeat(1000), 0.9);
        assertThat(chunk.toSource().snippet()).hasSize(KnowledgeBaseService.SNIPPET_LENGTH + 1).endsWith("…");
        assertThat(new KnowledgeBaseService.RetrievedChunk(1L, 2L, "Tài liệu", "ngắn", 0.9).toSource().snippet())
                .isEqualTo("ngắn");
    }

    @Test
    void rateLimiterRejectsBurstsAndRecoversAfterAMinute() {
        var now = new java.util.concurrent.atomic.AtomicLong(0);
        var clock = new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(now.get()); }
        };
        var limiter = new ChatRateLimiter(2, clock);
        limiter.acquire(1L);
        limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
        limiter.acquire(2L); // khách khác không bị ảnh hưởng
        now.set(60_000);
        limiter.acquire(1L);
    }

    @Test
    void missingActiveConfigurationAndCustomerAdminAccessAreRejected() {
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        assertThatThrownBy(() -> chat.sendMessage(session.id(), new SendChatMessageRequest("Xin chào")))
                .isInstanceOf(AiServiceException.class);
        assertThatThrownBy(() -> chat.getStats()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> knowledge.getDocuments(null, null, 0, 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(gemini);
    }

    @Test
    void changeDuringAiCallRejectsStaleAnswer() {
        activateConfig();
        login(customer);
        var session = chat.createSession(new ChatSessionRequest(null));
        when(gemini.generateAnswer(any(), anyString())).thenAnswer(invocation -> {
            tx.executeWithoutResult(status -> sessions.findById(session.id()).orElseThrow().setTitle("Đã sửa"));
            return new GeminiService.Answer("Xin chào", "Chung", List.of());
        });
        assertThatThrownBy(() -> chat.sendMessage(session.id(), new SendChatMessageRequest("Xin chào")))
                .isInstanceOf(ChatbotConflictException.class);
        assertThat(chat.getSession(session.id()).messages()).isEmpty();
    }

    @Test
    void documentLifecycleGuardsProcessingAndDisabledSources() {
        login(admin);
        var uploaded = knowledge.uploadDocument(new KnowledgeDocumentRequest("Hướng dẫn logic"), textFile());
        verify(queuedWorker).process(uploaded.id());
        assertThat(uploaded.status()).isEqualTo("PROCESSING");
        assertThatThrownBy(() -> knowledge.setDocumentStatus(uploaded.id(), new UpdateDocumentStatusRequest(DocumentStatus.DISABLED)))
                .isInstanceOf(ChatbotConflictException.class);
        assertThatThrownBy(() -> knowledge.deleteDocument(uploaded.id())).isInstanceOf(ChatbotConflictException.class);
        stubEmbeddings();
        worker().process(uploaded.id());
        assertThat(knowledge.getDocumentChunks(uploaded.id()).getFirst().embedded()).isTrue();
        assertThat(knowledge.getDocuments("logic", DocumentStatus.INDEXED, 0, 10).content()).hasSize(1);
        when(gemini.embedQuery(anyString())).thenReturn(new float[]{1, 0});
        assertThat(knowledge.retrieve("logic", 1)).hasSize(1);
        knowledge.setDocumentStatus(uploaded.id(), new UpdateDocumentStatusRequest(DocumentStatus.DISABLED));
        clearInvocations(gemini);
        assertThat(knowledge.retrieve("logic", 1)).isEmpty();
        verifyNoInteractions(gemini);
        assertThatThrownBy(() -> knowledge.setDocumentStatus(uploaded.id(), new UpdateDocumentStatusRequest(DocumentStatus.UPLOADED)))
                .isInstanceOf(IllegalArgumentException.class);
        knowledge.setDocumentStatus(uploaded.id(), new UpdateDocumentStatusRequest(DocumentStatus.INDEXED));
        knowledge.reindexDocument(uploaded.id());
        worker().process(uploaded.id());
        assertThat(knowledge.getDocumentChunks(uploaded.id())).hasSize(1);
        knowledge.deleteDocument(uploaded.id());
        assertThatThrownBy(() -> storage.readText(uploaded.fileUrl(), "TXT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedIndexingReturnsUploadedAndCanBeRetried() {
        login(admin);
        var uploaded = knowledge.uploadDocument(new KnowledgeDocumentRequest("Lỗi mạng"), textFile());
        when(gemini.embedDocuments(anyList())).thenThrow(new AiServiceException("test failure"));
        worker().process(uploaded.id());
        assertThat(knowledge.getDocuments("Lỗi mạng", DocumentStatus.UPLOADED, 0, 10).content()).hasSize(1);
        assertThatThrownBy(() -> knowledge.setDocumentStatus(uploaded.id(), new UpdateDocumentStatusRequest(DocumentStatus.INDEXED)))
                .isInstanceOf(ChatbotConflictException.class);
        knowledge.reindexDocument(uploaded.id());
        doAnswer(invocation -> vectors(invocation.getArgument(0))).when(gemini).embedDocuments(anyList());
        worker().process(uploaded.id());
        assertThat(knowledge.getDocumentChunks(uploaded.id())).hasSize(1);
        knowledge.deleteDocument(uploaded.id());
    }

    @Test
    void malformedAndOversizedDocumentsAreRejectedBeforeStorage() {
        login(admin);
        var request = new KnowledgeDocumentRequest("Tài liệu");
        assertThatThrownBy(() -> knowledge.uploadDocument(request,
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> knowledge.uploadDocument(request,
                new MockMultipartFile("file", "huge.txt", "text/plain", new byte[(int) KnowledgeDocumentStorage.MAX_BYTES + 1])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> knowledge.uploadDocument(request,
                new MockMultipartFile("file", "file.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> knowledge.uploadDocument(request,
                new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{(byte) 0xff})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chunkingAndCosineHandleBoundariesAndInvalidVectors() {
        String text = "Logic và sáng tạo. ".repeat(200);
        var split = storage.splitText(text);
        assertThat(split.size()).isGreaterThan(1);
        assertThat(split).allSatisfy(part -> assertThat(part.length()).isBetween(1, 1200));
        assertThat(KnowledgeBaseService.cosine(new float[]{1, 0}, new float[]{1, 0})).isEqualTo(1);
        assertThat(KnowledgeBaseService.cosine(new float[]{1, 0}, new float[]{0, 1})).isZero();
        assertThat(KnowledgeBaseService.cosine(new float[]{1}, new float[]{1, 0})).isNaN();
        assertThat(KnowledgeBaseService.cosine(new float[]{1}, new float[]{Float.NaN})).isNaN();
    }

    private void activateConfig() {
        login(admin);
        configService.activateConfig(configService.createConfig(configRequest()).id());
    }

    private ChatbotConfigRequest configRequest() { return new ChatbotConfigRequest("gemini-flash-latest", 0.2, 1024, 4, null); }
    private MockMultipartFile textFile() {
        return new MockMultipartFile("file", "logic.txt", "text/plain", "Đồ chơi STEM giúp phát triển logic.".getBytes(StandardCharsets.UTF_8));
    }
    private void indexDocument() {
        login(admin);
        var uploaded = knowledge.uploadDocument(new KnowledgeDocumentRequest("STEM"), textFile());
        stubEmbeddings();
        worker().process(uploaded.id());
    }
    private void stubEmbeddings() {
        when(gemini.embedDocuments(anyList())).thenAnswer(invocation -> vectors(invocation.getArgument(0)));
    }
    private static List<float[]> vectors(List<String> texts) { return texts.stream().map(t -> new float[]{1, 0}).toList(); }
    private KnowledgeIndexingWorker worker() { return new KnowledgeIndexingWorker(documents, storage, gemini, manager); }

    private Long product() {
        return tx.execute(status -> {
            Category category = Category.builder().name(java.util.UUID.randomUUID().toString()).build();
            em.persist(category);
            Product product = Product.builder().name("Robot STEM").description("Logic sáng tạo").price(BigDecimal.TEN)
                    .stockQuantity(10).minAge(4).maxAge(12).category(category)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            em.persist(product); return product.getId();
        });
    }

    private void login(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
