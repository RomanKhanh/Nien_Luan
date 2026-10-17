package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.enums.SenderType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Chỉ chạy tầng JPA với H2: không khởi tạo Gemini, không gọi AI và không dùng database thật.
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class ChatbotRepositoriesTest {
    @Autowired private EntityManager em;
    @Autowired private ChatSessionRepository sessions;
    @Autowired private ChatMessageRepository messages;
    @Autowired private ChatbotConfigRepository configs;
    @Autowired private KnowledgeDocumentRepository documents;
    @Autowired private DocumentChunkRepository chunks;

    @Test
    void sessionsAreScopedToCustomerAndAllowGeneralOrChildSpecificChat() {
        Customer owner = customer("owner@test.local");
        Customer other = customer("other@test.local");
        ChildProfile child = ChildProfile.builder().customer(owner).name("Bé An")
                .birthDate(LocalDate.of(2020, 1, 1)).build();
        em.persist(child);
        ChatSession general = sessions.save(ChatSession.builder().customer(owner).title("Chung").build());
        ChatSession specific = sessions.save(ChatSession.builder().customer(owner).childProfile(child)
                .title("Cho bé").build());
        sessions.save(ChatSession.builder().customer(other).title("Khách khác").build());
        em.flush();
        em.clear();

        assertThat(sessions.findAllByCustomerIdOrderByStartedAtDescIdDesc(owner.getId()))
                .extracting(ChatSession::getId).containsExactly(specific.getId(), general.getId());
        assertThat(sessions.findByIdAndCustomerId(general.getId(), other.getId())).isEmpty();
        assertThat(sessions.findByIdAndCustomerId(general.getId(), owner.getId()).orElseThrow()
                .getChildProfile()).isNull();
        assertThat(sessions.findByIdAndCustomerId(specific.getId(), owner.getId()).orElseThrow()
                .getChildProfile().getName()).isEqualTo("Bé An");
    }

    @Test
    void historyOrdersEqualTimestampsByIdFetchesSuggestionsAndCascadesOnSessionDeletion() {
        Customer owner = customer("history@test.local");
        Category category = Category.builder().name("STEM").build();
        em.persist(category);
        Product product = Product.builder().name("Robot").price(BigDecimal.TEN).category(category)
                .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
        em.persist(product);
        ChatSession session = ChatSession.builder().customer(owner).build();
        session.addMessage(ChatMessage.builder().sender(SenderType.USER).content("Tư vấn giúp tôi").build());
        ChatMessage reply = ChatMessage.builder().sender(SenderType.BOT).content("Gợi ý robot").build();
        reply.getSuggestedProducts().add(product);
        session.addMessage(reply);
        sessions.saveAndFlush(session);
        em.createNativeQuery("update chat_messages set created_at = TIMESTAMP '2026-01-01 10:00:00' "
                + "where chat_session_id = :id").setParameter("id", session.getId()).executeUpdate();
        em.clear();

        var history = messages.findAllByChatSessionIdOrderByCreatedAtAscIdAsc(session.getId());
        assertThat(history).extracting(ChatMessage::getSender).containsExactly(SenderType.USER, SenderType.BOT);
        // Tách entity khỏi context để xác nhận sản phẩm gợi ý đã được fetch cùng lịch sử.
        em.clear();
        assertThat(history.get(1).getSuggestedProducts()).extracting(Product::getName).containsExactly("Robot");
        assertThat(messages.countBySender(SenderType.USER)).isEqualTo(1);

        sessions.deleteById(session.getId());
        em.flush();
        assertThat(messages.findAllByChatSessionIdOrderByCreatedAtAscIdAsc(session.getId())).isEmpty();
        assertThat(em.find(Product.class, product.getId())).isNotNull();
    }

    @Test
    void activeConfigurationIsOptionalAndIncludesItsAdmin() {
        Admin admin = admin();
        assertThat(configs.findByActiveTrue()).isEmpty();
        configs.save(ChatbotConfig.builder().admin(admin).modelName("inactive").build());
        ChatbotConfig active = configs.saveAndFlush(ChatbotConfig.builder().admin(admin)
                .modelName("gemini-flash-latest").active(true).topK(4).maxTokens(1024).build());
        em.clear();

        ChatbotConfig found = configs.findByActiveTrue().orElseThrow();
        em.clear();
        assertThat(found.getId()).isEqualTo(active.getId());
        assertThat(found.getAdmin().getEmail()).isEqualTo(admin.getEmail());
        assertThat(configs.findAllByOrderByUpdatedAtDescIdDesc()).hasSize(2);
    }

    @Test
    void chunksAreOrderedAndRagCanExcludeDisabledDocuments() {
        Admin admin = admin();
        KnowledgeDocument indexed = document(admin, DocumentStatus.INDEXED);
        KnowledgeDocument disabled = document(admin, DocumentStatus.DISABLED);
        addChunk(indexed, 1);
        addChunk(indexed, 0);
        addChunk(disabled, 0);
        documents.saveAllAndFlush(java.util.List.of(indexed, disabled));
        em.clear();

        assertThat(chunks.findAllByKnowledgeDocumentIdOrderByChunkIndexAsc(indexed.getId()))
                .extracting(DocumentChunk::getChunkIndex).containsExactly(0, 1);
        assertThat(chunks.findByKnowledgeDocumentStatusOrderByIdAsc(DocumentStatus.INDEXED,
                PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
        assertThat(documents.findByStatusOrderByUploadedAtDescIdDesc(DocumentStatus.DISABLED,
                PageRequest.of(0, 10)).getContent()).extracting(KnowledgeDocument::getId)
                .containsExactly(disabled.getId());

        documents.deleteById(indexed.getId());
        em.flush();
        assertThat(chunks.findAllByKnowledgeDocumentIdOrderByChunkIndexAsc(indexed.getId())).isEmpty();
        assertThat(chunks.findAllByKnowledgeDocumentIdOrderByChunkIndexAsc(disabled.getId())).hasSize(1);
    }

    private Customer customer(String email) {
        Customer customer = Customer.builder().email(email).fullName("Phụ huynh").password("test-only").build();
        em.persist(customer);
        return customer;
    }

    private Admin admin() {
        Admin admin = Admin.builder().email("admin-repository@test.local").fullName("Admin")
                .password("test-only").build();
        em.persist(admin);
        return admin;
    }

    private KnowledgeDocument document(Admin admin, DocumentStatus status) {
        return KnowledgeDocument.builder().admin(admin).title(status.name()).fileUrl("test.pdf")
                .fileType("PDF").status(status).build();
    }

    private void addChunk(KnowledgeDocument document, int index) {
        document.getChunks().add(DocumentChunk.builder().knowledgeDocument(document).chunkIndex(index)
                .content("Đoạn " + index).embedding(new float[]{0.1f, 0.2f}).build());
    }
}
