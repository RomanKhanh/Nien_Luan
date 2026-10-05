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
import java.util.HashMap;
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
import static org.mockito.Mockito.verify;

/**
 * Đăng ký phải kèm mã 6 số gửi tới email (bật app.registration.email-verification, profile test mặc định tắt).
 * MailService được thay bằng mock để đọc mã trong email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.registration.email-verification=true")
@ActiveProfiles("test")
class RegistrationEmailCodeTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    @Autowired
    private Environment environment;
    @MockitoBean
    private MailService mailService;

    private String baseUrl;

    record Res(int status, JsonNode body) {
        String message() {
            return body.path("message").asString();
        }
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        clearInvocations(mailService);
    }

    @Test
    void registrationNeedsTheCodeSentToTheEmail() throws Exception {
        String email = newEmail();
        assertThat(register(email, null).status()).isEqualTo(400);

        assertThat(sendCode(email).status()).isEqualTo(200);
        String code = codeFromMail(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        Res wrongCode = register(email, wrong);
        assertThat(wrongCode.status()).isEqualTo(400);
        assertThat(wrongCode.message()).isEqualTo("Verification code is incorrect");

        assertThat(register(email, code).status()).isEqualTo(201);
        assertThat(call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)).status())
                .isEqualTo(200);
        // email đã có tài khoản: không gửi mã nữa
        assertThat(sendCode(email).status()).isEqualTo(409);
    }

    @Test
    void codeOnlyWorksForItsEmailAndResendHasCooldown() throws Exception {
        String email = newEmail();
        sendCode(email);
        String code = codeFromMail(email);
        Res resend = sendCode(email);
        assertThat(resend.status()).isEqualTo(400);
        assertThat(resend.message()).startsWith("Please wait ");

        assertThat(register(newEmail(), code).status()).isEqualTo(400);
        assertThat(register(email, code).status()).isEqualTo(201);
    }

    @Test
    void tooManyWrongCodesCancelTheCode() throws Exception {
        String email = newEmail();
        sendCode(email);
        String code = codeFromMail(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertThat(register(email, wrong).message()).isEqualTo("Verification code is incorrect");
        }
        assertThat(register(email, wrong).message()).isEqualTo("Too many wrong codes, please request a new code");
        Res afterLimit = register(email, code);
        assertThat(afterLimit.status()).isEqualTo(400);
        assertThat(afterLimit.message()).isEqualTo("Verification code is invalid or has expired");
        // mã bị hủy nhưng thời gian chờ gửi lại vẫn còn: không lặp "sai 5 lần → gửi lại" để spam hộp thư được
        Res resend = sendCode(email);
        assertThat(resend.status()).isEqualTo(400);
        assertThat(resend.message()).isEqualTo("Please wait before requesting a new code");
    }

    // nhiều request đoán mã song song vẫn chỉ được tối đa MAX_ATTEMPTS lần, sau đó mã bị hủy
    @Test
    void concurrentGuessesCannotExceedTheAttemptLimit() throws Exception {
        String email = newEmail();
        sendCode(email);
        int code = Integer.parseInt(codeFromMail(email));

        List<String> messages = runConcurrently(20, i ->
                register(email, "%06d".formatted((code + 1 + i) % 1_000_000)).message());

        assertThat(messages).filteredOn("Verification code is incorrect"::equals).hasSizeLessThanOrEqualTo(4);
        assertThat(messages).filteredOn("Too many wrong codes, please request a new code"::equals).hasSize(1);
        assertThat(messages).allMatch(m -> m.equals("Verification code is incorrect")
                || m.equals("Too many wrong codes, please request a new code")
                || m.equals("Verification code is invalid or has expired"));
        // mã đúng cũng không dùng được nữa
        assertThat(register(email, "%06d".formatted(code)).message())
                .isEqualTo("Verification code is invalid or has expired");
    }

    // ===== helpers =====

    interface Call {
        String run(int index) throws Exception;
    }

    // chạy n request cùng lúc: các luồng chờ chung một tín hiệu rồi mới gửi
    private static List<String> runConcurrently(int n, Call call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.run(index);
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String codeFromMail(String email) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        // tiêu đề không chứa mã (tiêu đề hay bị ghi log, hiện ở thông báo điện thoại)
        verify(mailService).send(eq(email), eq("Mã xác nhận đăng ký BrainBlocks"), anyString(), text.capture());
        Matcher m = CODE.matcher(text.getValue());
        assertThat(m.find()).as("code in mail: " + text.getValue()).isTrue();
        return m.group(1);
    }

    private Res sendCode(String email) throws Exception {
        return call("POST", "/api/auth/register/send-code", Map.of("email", email));
    }

    private Res register(String email, String code) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "Code User", "email", email, "password", PASSWORD));
        if (code != null) {
            body.put("verificationCode", code);
        }
        return call("POST", "/api/auth/register", body);
    }

    private String newEmail() {
        return "code" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
    }

    private Res call(String method, String path, Object body) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        JsonNode node = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
        return new Res(res.statusCode(), node);
    }
}
