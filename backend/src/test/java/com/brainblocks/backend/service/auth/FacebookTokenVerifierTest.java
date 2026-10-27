package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.exception.FacebookSignInException;
import com.brainblocks.backend.exception.FacebookSignInUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FacebookTokenVerifierTest {
    private HttpClient client;
    private FacebookTokenVerifier verifier;
    private static final String VALID = "{\"data\":{\"is_valid\":true,\"app_id\":\"123\",\"type\":\"USER\",\"user_id\":\"42\",\"expires_at\":4102444800,\"data_access_expires_at\":4102444800}}";

    @BeforeEach void setup() {
        client = mock(HttpClient.class);
        verifier = new FacebookTokenVerifier("123", "secret", "v24.0", client);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int code, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test void verifiesIdentityAndUsesServerProof() throws Exception {
        doReturn(response(200, VALID), response(200, "{\"id\":\"42\",\"name\":\"Bao\",\"email\":\"bao@example.com\"}"))
                .when(client).send(any(), any(HttpResponse.BodyHandler.class));
        assertThat(verifier.verify("user-token").subject()).isEqualTo("42");
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues().get(0).headers().firstValue("Authorization")).contains("Bearer 123|secret");
        assertThat(requests.getAllValues().get(1).uri().toString()).contains("appsecret_proof=").doesNotContain("user-token", "secret&");
    }

    @Test void rejectsWrongAppExpiredInvalidAndNonUserTokensBeforeReadingProfile() throws Exception {
        for (String body : new String[]{VALID.replace("123", "other-app"), VALID.replace("4102444800", "1"),
                VALID.replace("true", "false"), VALID.replace("USER", "APP"), VALID.replace("42", "")}) {
            reset(client);
            doReturn(response(200, body)).when(client).send(any(), any(HttpResponse.BodyHandler.class));
            assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(FacebookSignInException.class);
            verify(client, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test void rejectsMismatchedIdentity() throws Exception {
        doReturn(response(200, VALID), response(200, "{\"id\":\"99\"}"))
                .when(client).send(any(), any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(FacebookSignInException.class);
    }

    @Test void distinguishesMissingDeclinedExpiredAndGrantedEmailPermissions() throws Exception {
        for (String status : new String[]{"granted", "declined", "expired", "missing"}) {
            String permissions = status.equals("missing") ? "{\"data\":[]}"
                    : "{\"data\":[{\"permission\":\"email\",\"status\":\"" + status + "\"}]}";
            reset(client);
            doReturn(response(200, VALID), response(200, "{\"id\":\"42\",\"name\":\"Bao\"}"), response(200, permissions))
                    .when(client).send(any(), any(HttpResponse.BodyHandler.class));
            var profile = verifier.verify("token");
            assertThat(profile.email()).isEmpty();
            assertThat(profile.emailPermission().name()).isEqualTo(status.equals("missing") ? "NOT_GRANTED" : status.toUpperCase());
            var requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(client, times(3)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(requests.getAllValues().get(2).uri().getPath()).endsWith("/me/permissions");
        }
    }

    @Test void diagnosticFailurePreservesVerifiedIdentityAndUsesDebugScopes() throws Exception {
        String debug = VALID.replace("\"is_valid\":true", "\"is_valid\":true,\"scopes\":[\"public_profile\",\"email\"]");
        doReturn(response(200, debug), response(200, "{\"id\":\"42\"}"), response(503, "unavailable"))
                .when(client).send(any(), any(HttpResponse.BodyHandler.class));
        var profile = verifier.verify("token");
        assertThat(profile.subject()).isEqualTo("42");
        assertThat(profile.emailPermission()).isEqualTo(FacebookTokenVerifier.EmailPermission.GRANTED);
    }

    @Test void unavailableWithoutConfigurationOrWhenProviderFails() throws Exception {
        assertThatThrownBy(() -> new FacebookTokenVerifier("", "", "v24.0", client).verify("token"))
                .isInstanceOf(FacebookSignInUnavailableException.class);
        verifyNoInteractions(client);
        doReturn(response(503, "unavailable")).when(client).send(any(), any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(FacebookSignInUnavailableException.class);
    }
}
