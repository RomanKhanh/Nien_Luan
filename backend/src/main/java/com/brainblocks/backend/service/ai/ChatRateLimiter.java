package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Giới hạn số câu hỏi mỗi khách gửi trong một phút, để một tài khoản không tiêu hết quota Gemini.
// Lưu trong bộ nhớ một server: đủ cho đồ án chạy một instance, khởi động lại thì đếm lại từ đầu.
@Component
public class ChatRateLimiter {
    private static final long WINDOW_MS = 60_000;
    private final Map<Long, Deque<Long>> requests = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    @Autowired
    public ChatRateLimiter(@Value("${app.chat.max-questions-per-minute:10}") int limit) {
        this(limit, Clock.systemUTC());
    }

    ChatRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public void acquire(Long customerId) {
        long now = clock.millis();
        requests.compute(customerId, (id, times) -> {
            Deque<Long> recent = times == null ? new ArrayDeque<>() : times;
            while (!recent.isEmpty() && recent.peekFirst() <= now - WINDOW_MS) recent.pollFirst();
            if (recent.size() >= limit) {
                throw new TooManyRequestsException("Too many questions, please wait a moment and try again");
            }
            recent.addLast(now);
            return recent;
        });
    }
}
