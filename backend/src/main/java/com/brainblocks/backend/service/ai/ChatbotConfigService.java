package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.dto.request.chat.ChatbotConfigRequest;
import com.brainblocks.backend.dto.response.chat.ChatbotConfigResponse;
import com.brainblocks.backend.entity.ChatbotConfig;
import com.brainblocks.backend.exception.AiServiceException;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.ChatbotConfigRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// @Service đăng ký bean; @Validated kiểm tra các DTO @Valid khi gọi qua Spring proxy.
@Service
@Validated
@RequiredArgsConstructor
public class ChatbotConfigService {
    private final ChatbotConfigRepository configs;
    private final AdminRepository admins;
    private final CurrentUserProvider currentUser;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<ChatbotConfigResponse> getConfigs() {
        return configs.findAllByOrderByUpdatedAtDescIdDesc().stream().map(this::toResponse).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ChatbotConfigResponse createConfig(@Valid ChatbotConfigRequest request) {
        ChatbotConfig config = ChatbotConfig.builder().admin(admins.findById(currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"))).active(false).build();
        apply(config, request);
        return toResponse(configs.saveAndFlush(config));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ChatbotConfigResponse updateConfig(Long id, @Valid ChatbotConfigRequest request) {
        configs.lockActivationAnchor();
        ChatbotConfig config = find(id);
        apply(config, request);
        return toResponse(configs.saveAndFlush(config));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ChatbotConfigResponse activateConfig(Long id) {
        // Một khóa chung làm hai admin kích hoạt song song phải chạy lần lượt.
        configs.lockActivationAnchor();
        ChatbotConfig chosen = find(id);
        for (ChatbotConfig config : configs.findAll()) {
            config.setActive(config.getId().equals(id));
        }
        configs.flush();
        return toResponse(chosen);
    }

    @Transactional(readOnly = true)
    public Settings getActiveSettings() {
        // Snapshot thuần dữ liệu, không giữ entity LAZY trong lúc chờ mạng Gemini.
        ChatbotConfig config = configs.findByActiveTrue()
                .orElseThrow(() -> new AiServiceException("No active chatbot configuration"));
        return new Settings(config.getModelName(), config.getTemperature(), config.getMaxTokens(),
                config.getTopK(), config.getSystemPrompt());
    }

    public record Settings(String modelName, double temperature, int maxTokens, int topK, String systemPrompt) {}

    private ChatbotConfig find(Long id) {
        return configs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chatbot config not found"));
    }

    private void apply(ChatbotConfig config, ChatbotConfigRequest request) {
        config.setModelName(request.modelName().trim());
        config.setTemperature(request.temperature());
        config.setMaxTokens(request.maxTokens());
        config.setTopK(request.topK());
        config.setSystemPrompt(request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? null : request.systemPrompt().trim());
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig config) {
        return new ChatbotConfigResponse(config.getId(), config.getModelName(), config.getTemperature(),
                config.getMaxTokens(), config.getTopK(), config.getSystemPrompt(), config.isActive(),
                config.getUpdatedAt(), config.getAdmin().getFullName());
    }
}
