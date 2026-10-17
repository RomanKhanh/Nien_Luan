package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.dto.request.chat.*;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.chat.*;
import com.brainblocks.backend.dto.response.product.ProductSkillImpactResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.SenderType;
import com.brainblocks.backend.exception.AiServiceException;
import com.brainblocks.backend.exception.ChatbotConflictException;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.skill.SkillProfileService;
import com.brainblocks.backend.service.skill.SkillRecommendationService;
import com.brainblocks.backend.util.SearchTextUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Validated
@Slf4j
@PreAuthorize("hasRole('CUSTOMER')")
public class ChatService {
    private static final JsonMapper JSON = new JsonMapper();
    private static final Pattern AGE = Pattern.compile("\\b(\\d{1,2})\\s*tuoi\\b");
    // Từ phổ biến trong câu hỏi tiếng Việt (đã bỏ dấu); khớp gần như mọi mô tả nên không dùng để tìm sản phẩm.
    private static final Set<String> STOP_WORDS = Set.of("cho", "cua", "nhung", "giup", "tuoi", "phat", "trien",
            "san", "pham", "nen", "mua", "them", "nao", "the", "nay", "thi", "khong", "duoc", "voi", "mot", "cac",
            "nhu", "hay", "toi", "minh", "con", "kha", "nang", "rat", "nhieu", "lam", "hon", "dang", "nhat", "sao",
            "biet", "muon", "can", "gia", "choi", "ban", "chao", "xin", "tim", "goi", "hoi", "nhe", "vay", "roi",
            "cung", "thich", "nam", "thang", "tot", "phu", "hop", "loai", "nhom", "chon", "dung", "de", "gium");
    private static final int MAX_CANDIDATES = 20;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final ProductSkillImpactRepository impacts;
    private final ChildProductRepository childProducts;
    private final CurrentUserProvider currentUser;
    private final ChildProfileAccessGuard childGuard;
    private final SkillProfileService skills;
    private final SkillRecommendationService recommendations;
    private final ChatbotConfigService configs;
    private final KnowledgeBaseService knowledge;
    private final GeminiService gemini;
    private final ChatRateLimiter rateLimiter;
    private final TransactionTemplate tx;

    // Constructor injection: Spring cung cấp repository/service; không tự tạo bằng new.
    public ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, CustomerRepository customers,
            ProductRepository products, ProductSkillImpactRepository impacts, ChildProductRepository childProducts,
            CurrentUserProvider currentUser, ChildProfileAccessGuard childGuard, SkillProfileService skills,
            SkillRecommendationService recommendations, ChatbotConfigService configs, KnowledgeBaseService knowledge,
            GeminiService gemini, ChatRateLimiter rateLimiter, PlatformTransactionManager manager) {
        this.sessions = sessions; this.messages = messages; this.customers = customers;
        this.products = products; this.impacts = impacts; this.childProducts = childProducts;
        this.currentUser = currentUser; this.childGuard = childGuard; this.skills = skills;
        this.recommendations = recommendations; this.configs = configs; this.knowledge = knowledge; this.gemini = gemini;
        this.rateLimiter = rateLimiter;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionSummaryResponse> getMySessions() {
        var found = sessions.findAllByCustomerIdOrderByStartedAtDescIdDesc(currentUser.getCurrentUserId());
        Map<Long, ChatMessageRepository.SessionMessages> counts = summaries(found);
        return found.stream().map(s -> summary(s, counts.get(s.getId()))).toList();
    }

    @Transactional
    public ChatSessionDetailResponse createSession(@Valid ChatSessionRequest request) {
        Customer customer = customers.findById(currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        ChatSession session = sessions.saveAndFlush(ChatSession.builder().customer(customer)
                .childProfile(resolveChild(request.childProfileId())).build());
        return detail(session, List.of());
    }

    @Transactional(readOnly = true)
    public ChatSessionDetailResponse getSession(Long id) {
        ChatSession session = owned(id);
        return detail(session, history(id));
    }

    @Transactional
    public ChatSessionSummaryResponse updateSession(Long id, @Valid ChatSessionRequest request) {
        sessions.lockId(id);
        ChatSession session = owned(id);
        session.setChildProfile(resolveChild(request.childProfileId()));
        sessions.flush();
        return summary(session, summaries(List.of(session)).get(id));
    }

    @Transactional
    public void deleteSession(Long id) {
        sessions.lockId(id);
        sessions.delete(owned(id)); // Entity cascade xóa tin nhắn và liên kết sản phẩm gợi ý.
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(Long childProfileId) {
        if (childProfileId == null) return List.of("Nên chọn đồ chơi STEM theo độ tuổi như thế nào?",
                "Đồ chơi nào hỗ trợ tư duy logic?", "Gợi ý đồ chơi phát triển sáng tạo.");
        ChildProfile child = childGuard.getOwnedChildProfile(childProfileId);
        return List.of("Gợi ý đồ chơi phù hợp cho bé " + child.getName() + " " + child.getAge() + " tuổi.",
                "Bé nên bổ sung nhóm kỹ năng nào tiếp theo?", "Giải thích hồ sơ kỹ năng của bé.",
                "Gợi ý lộ trình đồ chơi STEM cho bé.");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatReplyResponse sendMessage(Long id, @Valid SendChatMessageRequest request) {
        long started = System.nanoTime();
        Long customerId = currentUser.getCurrentUserId();
        rateLimiter.acquire(customerId);
        String question = request.content().trim();
        Snapshot snapshot = tx.execute(status -> prepare(id, question));
        List<KnowledgeBaseService.RetrievedChunk> retrieved = retrieve(question, snapshot.settings().topK());
        String context = snapshot.context() + "\nNguồn tri thức (JSON):\n"
                + JSON.writeValueAsString(retrieved.stream().map(r -> new KnowledgeContext(r.documentTitle(), r.content())).toList())
                + "\nCâu hỏi hiện tại:\n" + question;
        GeminiService.Answer answer = gemini.generateAnswer(snapshot.settings(), context);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        List<ChatSourceResponse> sources = retrieved.stream().map(KnowledgeBaseService.RetrievedChunk::toSource).toList();
        // Chỉ lưu cả cặp USER/BOT khi AI trả lời hợp lệ; lỗi AI không để lại tin USER dở dang.
        // Sau lời gọi mạng, khóa phiên và kiểm tra lại để tránh lưu câu trả lời với ngữ cảnh đã đổi.
        return tx.execute(status -> saveReply(id, customerId, question, answer, sources, snapshot, elapsed));
    }

    // Không truy xuất được kho tri thức (ví dụ hết quota embedding) thì vẫn trả lời bằng dữ liệu sản phẩm.
    private List<KnowledgeBaseService.RetrievedChunk> retrieve(String question, int topK) {
        try {
            return knowledge.retrieve(question, topK);
        } catch (AiServiceException ex) {
            log.warn("Knowledge retrieval skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    private Snapshot prepare(Long id, String question) {
        ChatSession session = owned(id);
        ChildProfile child = session.getChildProfile();
        String normalized = SearchTextUtils.normalize(question);
        StringBuilder context = new StringBuilder();
        Integer age = null;
        Set<Long> ownedProducts = Set.of();
        List<Long> recommended = List.of();
        if (child != null) {
            age = child.getAge();
            context.append("Hồ sơ bé: ").append(child.getName()).append(", tuổi: ").append(age);
            if (child.getNote() != null && !child.getNote().isBlank()) context.append(", ghi chú: ").append(child.getNote().strip());
            context.append('\n');
            context.append("Hồ sơ kỹ năng (JSON): ").append(JSON.writeValueAsString(skills.getSkillProfile(child.getId()))).append('\n');
            ownedProducts = childProducts.findAllWithProductByChildProfileId(child.getId()).stream()
                    .map(t -> t.getProduct().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
            // Có mô tả và chỉ số kỹ năng của đồ chơi đã có để AI phân tích và chọn món bổ sung.
            context.append("Đồ chơi bé đã có (JSON): ")
                    .append(JSON.writeValueAsString(productContexts(ownedProducts.stream().limit(MAX_CANDIDATES).toList())))
                    .append('\n');
            recommended = recommendations.recommendNext(child.getId(), 10).stream().map(r -> r.productId()).toList();
        } else {
            var matcher = AGE.matcher(normalized);
            if (matcher.find()) age = Integer.parseInt(matcher.group(1));
        }
        Set<Long> alreadyOwned = ownedProducts;
        List<Long> matched = matchProducts(normalized, age).stream().filter(p -> !alreadyOwned.contains(p)).toList();
        // Sản phẩm khớp câu hỏi đứng trước (phụ huynh hỏi đúng món đó), rồi tới gợi ý theo hồ sơ kỹ năng.
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>(matched.subList(0, Math.min(10, matched.size())));
        candidateIds.addAll(recommended);
        candidateIds.addAll(matched);
        if (candidateIds.isEmpty()) {
            // Câu hỏi không khớp sản phẩm nào: đưa danh mục hợp tuổi để AI hỏi thêm hoặc gợi ý chung.
            Integer targetAge = age;
            products.findAll((root, query, cb) -> cb.and(available(root, cb, targetAge).toArray(Predicate[]::new)), PageRequest.of(0, 12, Sort.by("id")))
                    .forEach(p -> { if (!alreadyOwned.contains(p.getId())) candidateIds.add(p.getId()); });
        }
        List<Long> allowedIds = candidateIds.stream().limit(MAX_CANDIDATES).toList();
        context.append("Sản phẩm được phép gợi ý (JSON): ").append(JSON.writeValueAsString(productContexts(allowedIds))).append('\n');
        context.append("Chủ đề cho phép: ").append(GeminiService.TOPICS).append('\n');
        var recent = new ArrayList<>(messages.recentMessages(id, PageRequest.of(0, 12)));
        Collections.reverse(recent);
        context.append("Lịch sử gần đây:\n");
        int remaining = 16000;
        for (var message : recent) {
            String text = truncate(message.getContent(), Math.min(remaining, 2000));
            context.append(message.getSender()).append(": ").append(text).append('\n');
            remaining -= text.length();
            if (remaining <= 0) break;
        }
        var meta = summaries(List.of(session)).get(id);
        return new Snapshot(session.getVersion(), meta == null ? null : meta.getLastMessageId(),
                configs.getActiveSettings(), Set.copyOf(allowedIds), context.toString());
    }

    // Sản phẩm đang bán, hợp tuổi, chứa ít nhất một từ khóa của câu hỏi; món khớp nhiều từ khóa hơn xếp trước.
    private List<Long> matchProducts(String normalized, Integer age) {
        List<String> terms = Arrays.stream(normalized.split("[^a-z0-9]+")).filter(t -> t.length() >= 3)
                .filter(t -> !STOP_WORDS.contains(t)).distinct().limit(12).toList();
        if (terms.isEmpty()) return List.of();
        Specification<Product> filter = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>(available(root, cb, age));
            conditions.add(cb.or(terms.stream()
                    .map(term -> cb.like(root.get("searchText"), "%" + KnowledgeBaseService.escapeLike(term) + "%", '\\'))
                    .toArray(Predicate[]::new)));
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        return products.findAll(filter, PageRequest.of(0, 60, Sort.by(Sort.Order.desc("id")))).stream()
                .sorted(Comparator.comparingLong((Product p) -> matchCount(p, terms)).reversed()
                        .thenComparing(Product::getId, Comparator.reverseOrder()))
                .map(Product::getId).toList();
    }

    private static long matchCount(Product product, List<String> terms) {
        String text = product.getSearchText() == null ? "" : product.getSearchText();
        return terms.stream().filter(text::contains).count();
    }

    private static List<Predicate> available(Root<Product> root, CriteriaBuilder cb, Integer age) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.isTrue(root.get("active")));
        conditions.add(cb.greaterThan(root.get("stockQuantity"), 0));
        if (age != null) {
            conditions.add(cb.lessThanOrEqualTo(root.get("minAge"), age));
            conditions.add(cb.greaterThanOrEqualTo(root.get("maxAge"), age));
        }
        return conditions;
    }

    private ChatReplyResponse saveReply(Long id, Long customerId, String question, GeminiService.Answer answer,
            List<ChatSourceResponse> sources, Snapshot snapshot, long elapsed) {
        sessions.lockId(id).orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        ChatSession session = sessions.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        var meta = summaries(List.of(session)).get(id);
        Long lastId = meta == null ? null : meta.getLastMessageId();
        if (session.getVersion() != snapshot.version() || !Objects.equals(lastId, snapshot.lastMessageId())) {
            throw new ChatbotConflictException("Chat context changed while answering; please try again");
        }
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (var suggestion : answer.products()) {
            if (suggestion != null && suggestion.productId() != null && snapshot.allowedIds().contains(suggestion.productId())) {
                reasons.putIfAbsent(suggestion.productId(), truncate(suggestion.reason(), 500));
            }
        }
        List<Product> selected = reasons.isEmpty() ? List.of() : products.findAllById(reasons.keySet()).stream()
                .filter(p -> p.isActive() && p.getStockQuantity() > 0).toList();
        Set<Long> validIds = selected.stream().map(Product::getId).collect(Collectors.toSet());
        reasons.keySet().retainAll(validIds);
        // Schema đã giới hạn topic; vẫn kiểm tra lại để thống kê không nhận chuỗi lạ.
        String topic = GeminiService.TOPICS.contains(answer.topic()) ? answer.topic() : GeminiService.OTHER_TOPIC;
        ChatMessage user = ChatMessage.builder().sender(SenderType.USER).content(question).build();
        ChatMessage bot = ChatMessage.builder().sender(SenderType.BOT).content(answer.content().trim())
                .topic(topic).responseTimeMs(Math.max(0, elapsed))
                .responseMetadataJson(JSON.writeValueAsString(new Metadata(reasons, sources))).build();
        bot.getSuggestedProducts().addAll(selected);
        session.addMessage(user);
        session.addMessage(bot);
        if (session.getTitle() == null) session.setTitle(truncate(question, 255));
        sessions.saveAndFlush(session);
        var responses = toMessages(List.of(user, bot));
        return new ChatReplyResponse(responses.get(0), responses.get(1));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<AdminChatSessionSummaryResponse> getSessionsForAdmin(String keyword, int page, int size) {
        String pattern = "%" + KnowledgeBaseService.escapeLike(keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        Specification<ChatSession> filter = (root, query, cb) -> {
            var child = root.join("childProfile", JoinType.LEFT);
            var content = query.subquery(Long.class);
            var message = content.from(ChatMessage.class);
            content.select(message.get("id")).where(cb.equal(message.get("chatSession"), root),
                    cb.like(cb.lower(message.get("content")), pattern, '\\'));
            return cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("customer").get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("customer").get("email")), pattern, '\\'),
                    cb.like(cb.lower(child.get("name")), pattern, '\\'), cb.exists(content));
        };
        var found = sessions.findAll(filter, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
        var counts = summaries(found.getContent());
        return PageResponse.of(found, s -> adminSummary(s, counts.get(s.getId())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminChatSessionDetailResponse getSessionForAdmin(Long id) {
        ChatSession session = sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        var detail = detail(session, history(id));
        Customer customer = session.getCustomer();
        return new AdminChatSessionDetailResponse(detail.id(), detail.title(), detail.startedAt(), detail.lastMessageAt(),
                detail.messageCount(), detail.childProfileId(), detail.childName(), customer.getId(), customer.getFullName(),
                customer.getEmail(), detail.messages());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ChatStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        var top = PageRequest.of(0, 5);
        Map<LocalDate, Long> daily = new HashMap<>();
        messages.dailyQuestions(today.minusDays(13).atStartOfDay()).forEach(row -> daily.put(row.getQuestionDate(), row.getTotal()));
        List<ChatStatsResponse.DailyStat> days = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            days.add(new ChatStatsResponse.DailyStat(date, daily.getOrDefault(date, 0L)));
        }
        Double average = messages.averageResponseTime(SenderType.BOT);
        return new ChatStatsResponse(sessions.count(), messages.countBySender(SenderType.USER),
                messages.countBySenderAndCreatedAtGreaterThanEqual(SenderType.USER, today.minusDays(6).atStartOfDay()),
                average == null ? 0 : average,
                messages.countByTopic(top).stream().map(t -> new ChatStatsResponse.TopicStat(t.getTopic(), t.getTotal())).toList(),
                messages.topQuestions(SenderType.USER, top).stream().map(t -> new ChatStatsResponse.QuestionStat(t.getQuestion(), t.getTotal())).toList(),
                messages.topSuggestedProducts(SenderType.BOT, top).stream().map(t -> new ChatStatsResponse.ProductStat(t.getProductId(), t.getProductName(), t.getTotal())).toList(), days);
    }

    private ChatSession owned(Long id) {
        return sessions.findByIdAndCustomerId(id, currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private ChildProfile resolveChild(Long id) { return id == null ? null : childGuard.getOwnedChildProfile(id); }
    private List<ChatMessage> history(Long id) { return messages.findAllByChatSessionIdOrderByCreatedAtAscIdAsc(id); }

    private Map<Long, ChatMessageRepository.SessionMessages> summaries(List<ChatSession> found) {
        return found.isEmpty() ? Map.of() : messages.summarizeSessions(found.stream().map(ChatSession::getId).toList()).stream()
                .collect(Collectors.toMap(ChatMessageRepository.SessionMessages::getSessionId, v -> v));
    }

    private ChatSessionSummaryResponse summary(ChatSession s, ChatMessageRepository.SessionMessages meta) {
        var child = s.getChildProfile();
        return new ChatSessionSummaryResponse(s.getId(), s.getTitle(), s.getStartedAt(), meta == null ? null : meta.getLastMessageAt(),
                meta == null ? 0 : meta.getTotal(), child == null ? null : child.getId(), child == null ? null : child.getName());
    }

    private AdminChatSessionSummaryResponse adminSummary(ChatSession s, ChatMessageRepository.SessionMessages meta) {
        var summary = summary(s, meta);
        Customer c = s.getCustomer();
        return new AdminChatSessionSummaryResponse(summary.id(), summary.title(), summary.startedAt(), summary.lastMessageAt(),
                summary.messageCount(), summary.childProfileId(), summary.childName(), c.getId(), c.getFullName(), c.getEmail());
    }

    private ChatSessionDetailResponse detail(ChatSession s, List<ChatMessage> history) {
        var child = s.getChildProfile();
        return new ChatSessionDetailResponse(s.getId(), s.getTitle(), s.getStartedAt(),
                history.isEmpty() ? null : history.getLast().getCreatedAt(), history.size(),
                child == null ? null : child.getId(), child == null ? null : child.getName(), toMessages(history));
    }

    private List<ChatMessageResponse> toMessages(List<ChatMessage> history) {
        List<Long> ids = history.stream().filter(m -> m.getSender() == SenderType.BOT)
                .flatMap(m -> m.getSuggestedProducts().stream()).map(Product::getId).distinct().toList();
        Map<Long, ChatSuggestedProductResponse> cards = productCards(ids).stream()
                .collect(Collectors.toMap(ChatSuggestedProductResponse::productId, p -> p));
        return history.stream().map(message -> toMessage(message, cards)).toList();
    }

    private ChatMessageResponse toMessage(ChatMessage message, Map<Long, ChatSuggestedProductResponse> cards) {
        Metadata metadata = message.getResponseMetadataJson() == null ? new Metadata(Map.of(), List.of())
                : JSON.readValue(message.getResponseMetadataJson(), Metadata.class);
        List<ChatSuggestedProductResponse> suggestions = message.getSender() != SenderType.BOT ? List.of()
                : message.getSuggestedProducts().stream().map(Product::getId).sorted().filter(cards::containsKey).map(id -> {
                    var card = cards.get(id);
                    return new ChatSuggestedProductResponse(id, card.name(), card.price(), card.minAge(), card.maxAge(),
                            card.thumbnailUrl(), card.skillImpacts(), metadata.reasons().get(id));
                }).toList();
        return new ChatMessageResponse(message.getId(), message.getSender().name(), message.getContent(), message.getTopic(),
                message.getCreatedAt(), message.getResponseTimeMs(), suggestions, metadata.sources());
    }

    // Thẻ sản phẩm hiển thị dưới câu trả lời; reason được gắn lại theo từng tin nhắn ở toMessage().
    private List<ChatSuggestedProductResponse> productCards(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var byId = products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, String> thumbnails = products.findThumbnails(ids).stream().collect(Collectors.toMap(
                ProductRepository.ProductThumbnail::getProductId, ProductRepository.ProductThumbnail::getUrl, (a, b) -> a));
        Map<Long, List<ProductSkillImpactResponse>> skillImpacts = skillImpacts(ids);
        return ids.stream().filter(byId::containsKey).map(id -> {
            Product p = byId.get(id);
            return new ChatSuggestedProductResponse(id, p.getName(), p.getPrice(), p.getMinAge(), p.getMaxAge(),
                    thumbnails.get(id), skillImpacts.getOrDefault(id, List.of()), null);
        }).toList();
    }

    // Dữ liệu sản phẩm gửi cho AI: thêm danh mục và mô tả để giải thích được lợi ích giáo dục (đề 2.3).
    private List<ProductContext> productContexts(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var byId = products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, List<ProductSkillImpactResponse>> skillImpacts = skillImpacts(ids);
        return ids.stream().filter(byId::containsKey).map(id -> {
            Product p = byId.get(id);
            return new ProductContext(id, p.getName(), p.getPrice(), p.getMinAge(), p.getMaxAge(),
                    p.getCategory() == null ? null : p.getCategory().getName(), truncate(p.getDescription(), 600),
                    skillImpacts.getOrDefault(id, List.of()));
        }).toList();
    }

    private Map<Long, List<ProductSkillImpactResponse>> skillImpacts(List<Long> ids) {
        return impacts.findAllWithSkillByProductIdIn(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId(), Collectors.mapping(i -> new ProductSkillImpactResponse(
                        i.getSkill().getId(), i.getSkill().getCode(), i.getSkill().getName(), i.getImpactIndex()), Collectors.toList())));
    }

    private static String truncate(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(value.length(), Math.max(0, limit)));
    }
    private record Snapshot(long version, Long lastMessageId, ChatbotConfigService.Settings settings, Set<Long> allowedIds, String context) {}
    public record ProductContext(Long productId, String name, BigDecimal price, int minAge, int maxAge, String category,
            String description, List<ProductSkillImpactResponse> skillImpacts) {}
    public record KnowledgeContext(String documentTitle, String content) {}
    public record Metadata(Map<Long, String> reasons, List<ChatSourceResponse> sources) {}
}
