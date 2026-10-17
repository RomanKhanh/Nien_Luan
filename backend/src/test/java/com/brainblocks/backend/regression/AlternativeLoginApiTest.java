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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Đăng nhập bằng mã qua email và bằng Google qua HTTP thật (controller, validation, SecurityConfig permitAll).
 * MailService được thay bằng mock để đọc mã trong email; profile test không cấu hình GOOGLE_CLIENT_ID.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AlternativeLoginApiTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final String SENT = "If the email is registered, a login code has been sent";
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
    void loginWithEmailCodeReturnsSameJwtShapeAsPasswordLogin() throws Exception {
        String email = register();

        Res sent = call("POST", "/api/auth/otp/send", Map.of("email", email.toUpperCase()), null);
        assertThat(sent.status()).isEqualTo(200);
        assertThat(sent.message()).isEqualTo(SENT);

        Res login = call("POST", "/api/auth/otp/verify", Map.of("email", email, "code", codeFromMail(email)), null);
        assertThat(login.status()).isEqualTo(200);
        JsonNode data = login.body().path("data");
        assertThat(data.path("role").asString()).isEqualTo("CUSTOMER");
        assertThat(data.path("userId").asLong()).isPositive();
        assertThat(call("GET", "/api/me", null, data.path("token").asString()).status()).isEqualTo(200);
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        String email = "nobody" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";

        Res sent = call("POST", "/api/auth/otp/send", Map.of("email", email), null);

        assertThat(sent.status()).isEqualTo(200);
        assertThat(sent.message()).isEqualTo(SENT);
        verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
        assertThat(call("POST", "/api/auth/otp/verify", Map.of("email", email, "code", "123456"), null).message())
                .isEqualTo("Login code is invalid or has expired");
    }

    @Test
    void codeCannotBeReused() throws Exception {
        String email = register();
        call("POST", "/api/auth/otp/send", Map.of("email", email), null);
        String code = codeFromMail(email);

        assertThat(call("POST", "/api/auth/otp/verify", Map.of("email", email, "code", code), null).status())
                .isEqualTo(200);
        Res again = call("POST", "/api/auth/otp/verify", Map.of("email", email, "code", code), null);
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.message()).isEqualTo("Login code is invalid or has expired");
    }

    @Test
    void verifyRequiresEmailAndCode() throws Exception {
        assertThat(call("POST", "/api/auth/otp/verify", Map.of("email", "a@test.local"), null).status())
                .isEqualTo(400);
        assertThat(call("POST", "/api/auth/otp/send", Map.of("email", "not-an-email"), null).status())
                .isEqualTo(400);
    }

    @Test
    void googleLoginIsUnavailableWithoutClientId() throws Exception {
        Res res = call("POST", "/api/auth/google", Map.of("idToken", "x.y.z"), null);

        assertThat(res.status()).isEqualTo(503);
        assertThat(res.message()).isEqualTo("Google sign-in is not available right now");
        assertThat(call("POST", "/api/auth/google", Map.of(), null).status()).isEqualTo(400);
    }

    private String register() throws Exception {
        String email = "otp" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        Res res = call("POST", "/api/auth/register",
                Map.of("fullName", "Otp User", "email", email, "password", PASSWORD), null);
        assertThat(res.status()).isEqualTo(201);
        return email;
    }

    private String codeFromMail(String email) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq(email), eq("Mã đăng nhập BrainBlocks"), anyString(), text.capture());
        Matcher m = CODE.matcher(text.getValue());
        assertThat(m.find()).as("code in mail: " + text.getValue()).isTrue();
        return m.group(1);
    }

    private Res call(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            r.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> res = HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
        return new Res(res.statusCode(), node);
    }
}
