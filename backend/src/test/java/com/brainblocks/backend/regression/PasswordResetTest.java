package com.brainblocks.backend.regression;

import com.brainblocks.backend.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Quên mật khẩu: link trong email đặt được mật khẩu mới đúng 1 lần, đăng xuất phiên cũ; email không có tài khoản báo
 * 404; không spam được; token đăng nhập không dùng thay token đặt lại được.
 * MailService được thay bằng mock để đọc link trong email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordResetTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final String NEW_PASSWORD = "N3wPassw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_.-]+)");

    @Autowired
    private Environment environment;
    @MockitoBean
    private MailService mailService;

    private String baseUrl;

    record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.path("data");
        }
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        clearInvocations(mailService);
    }

    @Test
    void resetLinkSetsNewPasswordOnceAndLogsOutOldSessions() throws Exception {
        String email = register();
        String oldSession = login(email, PASSWORD).data().path("token").asString();

        assertThat(call("POST", "/api/auth/forgot-password", null, Map.of("email", email.toUpperCase())).status())
                .isEqualTo(200);
        String token = tokenFromMail(email);

        Res reset = call("POST", "/api/auth/reset-password", null, Map.of("token", token, "newPassword", NEW_PASSWORD));
        assertThat(reset.status()).isEqualTo(200);

        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(login(email, NEW_PASSWORD).status()).isEqualTo(200);
        // phiên đăng nhập trước khi đặt lại bị đăng xuất
        assertThat(call("GET", "/api/me", oldSession, null).status()).isEqualTo(401);
        // link chỉ dùng được 1 lần
        assertThat(call("POST", "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "An0therPass!")).status()).isEqualTo(400);
    }

    @Test
    void unknownEmailIsReportedAndGetsNoMail() throws Exception {
        Res res = call("POST", "/api/auth/forgot-password", null, Map.of("email", "nobody-" + SEQ.incrementAndGet()
                + "@test.local"));
        assertThat(res.status()).isEqualTo(404);
        assertThat(res.body().path("message").asString()).isEqualTo("Account not found");
        verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void repeatedRequestsWithinCooldownSendOneMail() throws Exception {
        String email = register();
        assertThat(call("POST", "/api/auth/forgot-password", null, Map.of("email", email)).status()).isEqualTo(200);
        Res again = call("POST", "/api/auth/forgot-password", null, Map.of("email", email));
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.body().path("message").asString()).isEqualTo("Please wait before requesting a new reset link");
        verify(mailService, times(1)).send(eq(email), anyString(), anyString(), anyString());
    }

    @Test
    void badTokensAndWeakPasswordsAreRejected() throws Exception {
        String email = register();
        String loginToken = login(email, PASSWORD).data().path("token").asString();

        // token đăng nhập ký bằng khóa khác, không dùng làm token đặt lại được
        assertThat(call("POST", "/api/auth/reset-password", null,
                Map.of("token", loginToken, "newPassword", NEW_PASSWORD)).status()).isEqualTo(400);
        assertThat(call("POST", "/api/auth/reset-password", null,
                Map.of("token", "not-a-token", "newPassword", NEW_PASSWORD)).status()).isEqualTo(400);

        call("POST", "/api/auth/forgot-password", null, Map.of("email", email));
        String token = tokenFromMail(email);
        assertThat(call("POST", "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "short")).status()).isEqualTo(400);
        // mật khẩu quá ngắn bị từ chối trước khi dùng link, nên link vẫn còn dùng được
        assertThat(call("POST", "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", NEW_PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void lockedAccountIsReportedAndGetsNoMail() throws Exception {
        String email = register();
        long userId = login(email, PASSWORD).data().path("userId").asLong();
        String admin = login("admin@test.local", "test-admin-password").data().path("token").asString();
        assertThat(call("PATCH", "/api/admin/users/" + userId + "/status", admin, Map.of("enabled", false)).status())
                .isEqualTo(200);

        Res res = call("POST", "/api/auth/forgot-password", null, Map.of("email", email));
        assertThat(res.status()).isEqualTo(403);
        assertThat(res.body().path("message").asString()).isEqualTo("Account is locked");
        verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    // cùng 1 link gửi song song nhiều lần: chỉ đúng 1 lần đặt được mật khẩu, các lần kia báo link không hợp lệ
    @Test
    void sameLinkUsedConcurrentlySucceedsOnce() throws Exception {
        // lặp vài vòng vì lỗi tranh chấp chỉ lộ ra khi các request thật sự chồng lên nhau
        for (int round = 0; round < 5; round++) {
            String email = register();
            call("POST", "/api/auth/forgot-password", null, Map.of("email", email));
            String token = tokenFromMail(email);

            List<Integer> statuses = runConcurrently(6, i -> call("POST", "/api/auth/reset-password", null,
                    Map.of("token", token, "newPassword", "Concurrent" + i + "Pass")).status());

            assertThat(statuses).as("round " + round).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).as("round " + round).filteredOn(s -> s != 200).allMatch(s -> s == 400);
            clearInvocations(mailService);
        }
    }

    // ===== helpers =====

    interface Call {
        int run(int index) throws Exception;
    }

    // chạy n request cùng lúc: các luồng chờ chung một tín hiệu rồi mới gửi
    private static List<Integer> runConcurrently(int n, Call call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.run(index);
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String tokenFromMail(String email) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq(email), eq("Đặt lại mật khẩu BrainBlocks"), anyString(), text.capture());
        Matcher m = TOKEN_IN_LINK.matcher(text.getValue());
        assertThat(m.find()).as("link in mail: " + text.getValue()).isTrue();
        return m.group(1);
    }

    private String register() throws Exception {
        String email = "reset" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Reset User", "email", email, "password", PASSWORD)).status()).isEqualTo(201);
        return email;
    }

    private Res login(String email, String password) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
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
}
