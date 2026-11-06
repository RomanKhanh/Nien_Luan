package com.brainblocks.backend.fulltest.users;

import com.brainblocks.backend.entity.RevokedToken;
import com.brainblocks.backend.repository.RevokedTokenRepository;
import com.brainblocks.backend.security.JwtService;
import com.brainblocks.backend.service.ai.GeminiService;
import com.brainblocks.backend.service.auth.LogoutService;
import com.brainblocks.backend.service.mail.MailService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy toàn bộ luồng HTTP và JWT thật, chỉ lưu dữ liệu trong H2 của profile test.
@SpringBootTest
@ActiveProfiles("test")
class LogoutApiTest {
    private static final String PASSWORD = "Passw0rd!";
    private static final JsonMapper JSON = new JsonMapper();

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired JwtService jwtService;
    @Autowired LogoutService logoutService;
    @Autowired RevokedTokenRepository revokedTokens;
    @Value("${jwt.secret}") String jwtSecret;
    @MockitoBean MailService mailService;
    @MockitoBean GeminiService geminiService;
    private MockMvc mvc;

    record Response(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }
    record Account(String email, String token) {}

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void logoutRevokesCurrentTokenAndStoresItsExactExpiration() throws Exception {
        Account account = customer();
        expect(call("GET", "/api/me", account.token(), null), 200);

        expectLogout(account.token());

        expect(call("GET", "/api/me", account.token(), null), 401);
        RevokedToken revoked = revokedTokens.findById(jwtService.extractJti(account.token())).orElseThrow();
        assertThat(revoked.getExpiresAt()).isEqualTo(LocalDateTime.ofInstant(
                jwtService.extractExpiration(account.token()).toInstant(), ZoneOffset.UTC));
    }

    @Test
    void logoutLeavesOtherDeviceOfSameAccountAuthenticated() throws Exception {
        Account account = customer();
        String secondToken = login(account.email());
        assertThat(jwtService.extractJti(account.token())).isNotEqualTo(jwtService.extractJti(secondToken));
        assertThat(UUID.fromString(jwtService.extractJti(account.token())).toString())
                .isEqualTo(jwtService.extractJti(account.token()));

        expectLogout(account.token());

        expect(call("GET", "/api/me", account.token(), null), 401);
        expect(call("GET", "/api/me", secondToken, null), 200);
        assertThat(revokedTokens.existsById(jwtService.extractJti(secondToken))).isFalse();
    }

    @Test
    void missingGarbageEmptyAndRepeatedLogoutAlwaysSucceed() throws Exception {
        expectLogout(null);
        expectLogout("not-a-jwt");
        expectLogout("");
        Account account = customer();
        expectLogout(account.token());
        RevokedToken first = revokedTokens.findById(jwtService.extractJti(account.token())).orElseThrow();

        expectLogout(account.token());

        assertThat(revokedTokens.findById(first.getJti()).orElseThrow().getExpiresAt())
                .isEqualTo(first.getExpiresAt());
        expect(call("GET", "/api/me", account.token(), null), 401);
    }

    @Test
    void freshLoginAfterLogoutAuthenticatesWithoutChangingPasswordVersion() throws Exception {
        Account account = customer();
        expectLogout(account.token());

        String fresh = login(account.email());

        assertThat(jwtService.extractJti(fresh)).isNotEqualTo(jwtService.extractJti(account.token()));
        expect(call("GET", "/api/me", fresh, null), 200);
        expect(call("GET", "/api/me", account.token(), null), 401);
    }

    @Test
    void purgeRemovesExpiredRowsAndKeepsUnexpiredRows() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String expired = UUID.randomUUID().toString();
        String active = UUID.randomUUID().toString();
        revokedTokens.save(new RevokedToken(expired, now.minusHours(1)));
        revokedTokens.save(new RevokedToken(active, now.plusHours(1)));

        logoutService.purgeExpiredTokens();

        assertThat(revokedTokens.existsById(expired)).isFalse();
        assertThat(revokedTokens.existsById(active)).isTrue();
    }

    @Test
    void expiredAndTamperedTokensAreNotStored() throws Exception {
        Account account = customer();
        String expiredJti = UUID.randomUUID().toString();
        String expired = signedToken(account.email(), expiredJti, Instant.now().minusSeconds(60));
        String valid = account.token();
        int signature = valid.lastIndexOf('.') + 1;
        String tampered = valid.substring(0, signature)
                + (valid.charAt(signature) == 'A' ? "B" : "A") + valid.substring(signature + 1);

        expectLogout(expired);
        expectLogout(tampered);

        assertThat(revokedTokens.existsById(expiredJti)).isFalse();
        assertThat(revokedTokens.existsById(jwtService.extractJti(valid))).isFalse();
        expect(call("GET", "/api/me", valid, null), 200);
    }

    @Test
    void legacyTokenWithoutJtiRemainsUsableAfterLogout() throws Exception {
        Account account = customer();
        String legacy = signedToken(account.email(), null, Instant.now().plusSeconds(3600));
        assertThat(jwtService.extractJti(legacy)).isNull();
        expect(call("GET", "/api/me", legacy, null), 200);

        expectLogout(legacy);

        expect(call("GET", "/api/me", legacy, null), 200);
    }

    private String signedToken(String email, String jti, Instant expiration) {
        var builder = Jwts.builder().subject(email).claim("ver", 0)
                .expiration(Date.from(expiration));
        if (jti != null) builder.id(jti);
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).compact();
    }

    private void expectLogout(String token) throws Exception {
        Response response = call("POST", "/api/auth/logout", token, null);
        expect(response, 200);
        assertThat(response.body().path("success").asBoolean()).isTrue();
        assertThat(response.body().path("message").asString()).isEqualTo("Logged out");
    }

    private Account customer() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@test.local";
        expect(call("POST", "/api/auth/register", null,
                Map.of("fullName", "Logout Parent", "email", email, "password", PASSWORD)), 201);
        return new Account(email, login(email));
    }

    private String login(String email) throws Exception {
        Response response = call("POST", "/api/auth/login", null, Map.of("email", email, "password", PASSWORD));
        expect(response, 200);
        return response.data().path("token").asString();
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(JSON.writeValueAsString(body));
        var result = mvc.perform(request).andReturn().getResponse();
        String content = result.getContentAsString();
        return new Response(result.getStatus(), content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content));
    }

    private void expect(Response response, int status) {
        assertThat(response.status()).as("response: %s", response.body()).isEqualTo(status);
    }
}
