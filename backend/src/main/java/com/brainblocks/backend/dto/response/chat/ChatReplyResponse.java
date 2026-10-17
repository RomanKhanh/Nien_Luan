package com.brainblocks.backend.dto.response.chat;

public record ChatReplyResponse(ChatMessageResponse userMessage, ChatMessageResponse botMessage) {}
