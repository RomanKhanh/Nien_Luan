package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.exception.FacebookSignInException;
import com.brainblocks.backend.exception.FacebookSignInUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Server-side verification; never trust a browser-supplied Facebook user id or email. */
@Component
public class FacebookTokenVerifier {
    private final String appId;
    private final String appSecret;
    private final String version;
    private final HttpClient client;
    private final JsonMapper json = new JsonMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public FacebookTokenVerifier(@Value("${app.facebook.app-id:}") String appId,
                                 @Value("${app.facebook.app-secret:}") String appSecret,
                                 @Value("${app.facebook.api-version:v24.0}") String version) {
        this(appId, appSecret, version, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    FacebookTokenVerifier(String appId, String appSecret, String version, HttpClient client) {
        this.appId = appId.trim();
        this.appSecret = appSecret.trim();
        this.version = version;
        this.client = client;
    }

    public Profile verify(String token) {
        if (appId.isBlank() || appSecret.isBlank() || !version.matches("v\\d+\\.0")) {
            throw new FacebookSignInUnavailableException();
        }
        JsonNode data = get("debug_token?input_token=" + encode(token), appId + "|" + appSecret).path("data");
        long now = Instant.now().getEpochSecond();
        String subject = data.path("user_id").asText("");
        if (!data.path("is_valid").asBoolean() || !appId.equals(data.path("app_id").asText())
                || !"USER".equals(data.path("type").asText()) || subject.isBlank()
                || data.path("expires_at").asLong() <= now
                || (data.path("data_access_expires_at").asLong() > 0
                    && data.path("data_access_expires_at").asLong() <= now)) {
            throw invalid();
        }
        JsonNode profile = get("me?fields=id,name,email&appsecret_proof=" + proof(token), token);
        if (!subject.equals(profile.path("id").asText())) throw invalid();
        String email = profile.path("email").asText("");
        EmailPermission permission = EmailPermission.UNKNOWN;
        if (email.isBlank()) {
            // Only fetch permission diagnostics when /me omitted the email. Never log provider data.
            permission = emailPermission(token, data);
        }
        return new Profile(subject, profile.path("name").asText(""), email, permission);
    }

    private EmailPermission emailPermission(String token, JsonNode debugData) {
        EmailPermission fallback = EmailPermission.UNKNOWN;
        if (debugData.path("scopes").isArray()) {
            fallback = EmailPermission.NOT_GRANTED;
            for (JsonNode scope : debugData.path("scopes")) {
                if ("email".equals(scope.asText())) fallback = EmailPermission.GRANTED;
            }
        }
        try {
            JsonNode permissions = get("me/permissions?appsecret_proof=" + proof(token), token, 3).path("data");
            if (!permissions.isArray()) return fallback;
            for (JsonNode entry : permissions) {
                if (!"email".equals(entry.path("permission").asText())) continue;
                return switch (entry.path("status").asText()) {
                    case "granted" -> EmailPermission.GRANTED;
                    case "declined" -> EmailPermission.DECLINED;
                    case "expired" -> EmailPermission.EXPIRED;
                    default -> EmailPermission.UNKNOWN;
                };
            }
            return fallback == EmailPermission.GRANTED ? fallback : EmailPermission.NOT_GRANTED;
        } catch (FacebookSignInException | FacebookSignInUnavailableException ignored) {
            // Optional diagnostics must not prevent an already-linked user from signing in.
            return fallback;
        }
    }

    private JsonNode get(String path, String bearer) {
        return get(path, bearer, 10);
    }

    private JsonNode get(String path, String bearer, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + version + "/" + path))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization", "Bearer " + bearer).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500) throw new FacebookSignInUnavailableException();
            if (response.statusCode() != 200) throw invalid();
            JsonNode body = json.readTree(response.body());
            if (body.has("error")) throw invalid();
            return body;
        } catch (FacebookSignInException | FacebookSignInUnavailableException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FacebookSignInUnavailableException();
        } catch (Exception ex) {
            // Do not log request URLs, access tokens, provider responses, or app secrets.
            throw new FacebookSignInUnavailableException();
        }
    }

    private String proof(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new FacebookSignInUnavailableException();
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static FacebookSignInException invalid() { return new FacebookSignInException("Invalid Facebook credential"); }
    public enum EmailPermission { GRANTED, NOT_GRANTED, DECLINED, EXPIRED, UNKNOWN }
    public record Profile(String subject, String name, String email, EmailPermission emailPermission) {
        public Profile(String subject, String name, String email) {
            this(subject, name, email, EmailPermission.UNKNOWN);
        }
    }
}
