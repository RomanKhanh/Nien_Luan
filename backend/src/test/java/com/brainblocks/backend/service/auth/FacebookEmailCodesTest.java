package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FacebookEmailCodesTest {
    private MailService mail;
    private Clock clock;
    private FacebookEmailCodes codes;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() {
        mail = mock(MailService.class); clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        codes = new FacebookEmailCodes(mail, clock);
    }
    private String send() {
        codes.send("fb1", "a@example.com");
        var body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("a@example.com"), anyString(), anyString(), body.capture());
        var match = Pattern.compile("\\b[0-9]{6}\\b").matcher(body.getValue());
        assertThat(match.find()).isTrue();
        return match.group();
    }
    @Test void boundToIdentityAndMailboxAndConsumedOnce() {
        String code = send();
        assertThatThrownBy(() -> codes.verify("fb2", "a@example.com", code)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codes.verify("fb1", "b@example.com", code)).isInstanceOf(IllegalArgumentException.class);
        codes.verify("fb1", "a@example.com", code);
        assertThatThrownBy(() -> codes.verify("fb1", "a@example.com", code)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiresAfterFiveMinutes() {
        String code = send(); when(clock.instant()).thenReturn(now.plusSeconds(300));
        assertThatThrownBy(() -> codes.verify("fb1", "a@example.com", code)).hasMessageContaining("expired");
    }
    @Test void fiveWrongAttemptsInvalidateEvenCorrectCode() {
        String code = send();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> codes.verify("fb1", "a@example.com", "wrong")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> codes.verify("fb1", "a@example.com", code)).hasMessageContaining("expired");
    }
    @Test void cooldownProtectsBothMailboxAndFacebookIdentity() {
        send();
        assertThatThrownBy(() -> codes.send("fb2", "a@example.com")).hasMessageContaining("wait");
        assertThatThrownBy(() -> codes.send("fb1", "b@example.com")).hasMessageContaining("wait");
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        codes.send("fb1", "a@example.com");
    }
}
