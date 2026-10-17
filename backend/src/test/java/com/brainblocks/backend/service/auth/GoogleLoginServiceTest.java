package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.config.GoogleAuthConfig;
import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.GoogleSignInException;
import com.brainblocks.backend.exception.GoogleSignInUnavailableException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GoogleLoginService với verifier giả (không gọi tới Google): token được coi là hợp lệ khi verifier giả trả true.
 * Riêng ca sai audience dùng verifier thật của GoogleAuthConfig: audience được kiểm tra trước chữ ký nên không cần mạng.
 */
class GoogleLoginServiceTest {
    private static final String CLIENT_ID = "brainblocks-test.apps.googleusercontent.com";
    private static final String SUBJECT = "google-sub-123";

    private GoogleIdTokenVerifier verifier;
    private ObjectProvider<GoogleIdTokenVerifier> provider;
    private UserRepository userRepository;
    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private GoogleLoginService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        verifier = mock(GoogleIdTokenVerifier.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(verifier);
        userRepository = mock(UserRepository.class);
        authService = mock(AuthService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}random");
        when(userRepository.findByGoogleSubject(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(authService.issueToken(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return new LoginResponse("jwt-for-" + user.getEmail(), user.getRole().name(), user.getId());
        });
        service = new GoogleLoginService(provider, userRepository, authService, passwordEncoder);
    }

    @Test
    void rejectsTokenIssuedForAnotherAudienceWithRealVerifier() {
        GoogleIdTokenVerifier real = new GoogleAuthConfig().googleIdTokenVerifier(CLIENT_ID);
        when(provider.getIfAvailable()).thenReturn(real);

        String token = idToken("another-app.apps.googleusercontent.com", "bao@gmail.com", true);

        assertThatThrownBy(() -> service.login(token))
                .isInstanceOf(GoogleSignInException.class)
                .hasMessage("Invalid Google credential");
        verifyNoInteractions(userRepository, authService);
    }

    @Test
    void rejectsTokenWhenVerifierSaysInvalid() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(false);

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", true)))
                .isInstanceOf(GoogleSignInException.class)
                .hasMessage("Invalid Google credential");
        verifyNoInteractions(userRepository, authService);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> service.login("not-a-jwt"))
                .isInstanceOf(GoogleSignInException.class)
                .hasMessage("Invalid Google credential");
    }

    @Test
    void reportsUnavailableWhenClientIdIsNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", true)))
                .isInstanceOf(GoogleSignInUnavailableException.class);
    }

    @Test
    void reportsUnavailableWhenGoogleKeysCannotBeFetched() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", true)))
                .isInstanceOf(GoogleSignInUnavailableException.class);
    }

    @Test
    void rejectsUnverifiedEmail() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", false)))
                .isInstanceOf(GoogleSignInException.class)
                .hasMessage("Google email is not verified");
        verifyNoInteractions(authService);
    }

    @Test
    void createsCustomerForNewEmail() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);
        when(authService.createCustomer(any())).thenAnswer(inv -> {
            Customer customer = inv.getArgument(0);
            customer.setId(42L);
            return customer;
        });

        LoginResponse res = service.login(idToken(CLIENT_ID, "  New.Parent@Gmail.com ", true));

        ArgumentCaptor<Customer> created = ArgumentCaptor.forClass(Customer.class);
        verify(authService).createCustomer(created.capture());
        assertThat(created.getValue().getEmail()).isEqualTo("new.parent@gmail.com");
        assertThat(created.getValue().getFullName()).isEqualTo("Phụ huynh Test");
        assertThat(created.getValue().getGoogleSubject()).isEqualTo(SUBJECT);
        // không có mật khẩu dùng được: băm của chuỗi ngẫu nhiên
        assertThat(created.getValue().getPassword()).isEqualTo("{bcrypt}random");
        assertThat(res.token()).isEqualTo("jwt-for-new.parent@gmail.com");
        assertThat(res.role()).isEqualTo("CUSTOMER");
        assertThat(res.userId()).isEqualTo(42L);
    }

    @Test
    void linksExistingAccountByEmail() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);
        Customer existing = customer(7L, "bao@gmail.com", null, true);
        when(userRepository.findByEmail("bao@gmail.com")).thenReturn(Optional.of(existing));

        LoginResponse res = service.login(idToken(CLIENT_ID, "bao@gmail.com", true));

        assertThat(existing.getGoogleSubject()).isEqualTo(SUBJECT);
        assertThat(existing.getPassword()).isEqualTo("old-hash"); // mật khẩu cũ giữ nguyên
        assertThat(res.userId()).isEqualTo(7L);
        verify(authService, never()).createCustomer(any());
    }

    @Test
    void logsInAccountAlreadyLinkedBySubject() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);
        Customer linked = customer(8L, "old-address@gmail.com", SUBJECT, true);
        when(userRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.of(linked));

        LoginResponse res = service.login(idToken(CLIENT_ID, "renamed@gmail.com", true));

        assertThat(res.userId()).isEqualTo(8L);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void rejectsEmailLinkedToAnotherGoogleAccount() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);
        Customer existing = customer(9L, "bao@gmail.com", "other-sub", true);
        when(userRepository.findByEmail("bao@gmail.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", true)))
                .isInstanceOf(GoogleSignInException.class)
                .hasMessage("This email is linked to a different Google account");
        assertThat(existing.getGoogleSubject()).isEqualTo("other-sub");
        verify(authService, never()).issueToken(any());
    }

    @Test
    void rejectsLockedAccountWithoutLinking() throws Exception {
        when(verifier.verify(any(GoogleIdToken.class))).thenReturn(true);
        Customer locked = customer(10L, "bao@gmail.com", null, false);
        when(userRepository.findByEmail("bao@gmail.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.login(idToken(CLIENT_ID, "bao@gmail.com", true)))
                .isInstanceOf(DisabledException.class)
                .hasMessage("Account is locked");
        assertThat(locked.getGoogleSubject()).isNull();
        verify(authService, never()).issueToken(any());
    }

    private static Customer customer(Long id, String email, String subject, boolean enabled) {
        return Customer.builder().id(id).fullName("Bảo").email(email).password("old-hash")
                .googleSubject(subject).enabled(enabled).build();
    }

    // JWT không ký thật (chữ ký giả): verifier giả không kiểm tra chữ ký, verifier thật dừng ở bước kiểm tra audience
    private static String idToken(String audience, String email, boolean emailVerified) {
        long now = Instant.now().getEpochSecond();
        String header = "{\"alg\":\"RS256\",\"kid\":\"test\",\"typ\":\"JWT\"}";
        String payload = """
                {"iss":"https://accounts.google.com","aud":"%s","sub":"%s","email":"%s","email_verified":%s,\
                "name":"Phụ huynh Test","iat":%d,"exp":%d}""".formatted(audience, SUBJECT, email, emailVerified,
                now, now + 600);
        return b64(header) + "." + b64(payload) + "." + b64("signature");
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
