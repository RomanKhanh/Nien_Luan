package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.MailNotConfiguredException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.DisabledException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LoginOtpService với đồng hồ giả: hết hạn 5 phút, cooldown 60 giây, sai tối đa 5 lần, mỗi mã dùng 1 lần. */
class LoginOtpServiceTest {
    private static final String EMAIL = "bao@gmail.com";
    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    private MutableClock clock;
    private UserRepository userRepository;
    private MailService mailService;
    private AuthService authService;
    private LoginOtpService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"));
        userRepository = mock(UserRepository.class);
        mailService = mock(MailService.class);
        authService = mock(AuthService.class);
        customer = Customer.builder().id(5L).fullName("Bảo").email(EMAIL).password("hash").build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(customer));
        when(authService.issueToken(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return new LoginResponse("jwt-" + user.getId(), user.getRole().name(), user.getId());
        });
        service = new LoginOtpService(userRepository, mailService, authService, clock, 5, 60);
    }

    @Test
    void sendsCodeToActiveAccountAndLogsIn() {
        service.sendCode("  Bao@Gmail.com ");
        String code = lastMailedCode();

        LoginResponse res = service.verify(EMAIL, code);

        assertThat(res.token()).isEqualTo("jwt-5");
        assertThat(res.role()).isEqualTo("CUSTOMER");
        verify(authService).issueToken(customer);
    }

    @Test
    void mailContainsCodeButSubjectDoesNot() {
        service.sendCode(EMAIL);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), subject.capture(), anyString(), text.capture());
        assertThat(CODE.matcher(subject.getValue()).find()).isFalse();
        assertThat(CODE.matcher(text.getValue()).find()).isTrue();
    }

    @Test
    void unknownAndLockedEmailsGetSameAnswerButNoMail() {
        Customer locked = Customer.builder().id(6L).fullName("Khóa").email("locked@gmail.com").password("hash")
                .enabled(false).build();
        when(userRepository.findByEmail("locked@gmail.com")).thenReturn(Optional.of(locked));

        assertThatCode(() -> service.sendCode("nobody@gmail.com")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendCode("locked@gmail.com")).doesNotThrowAnyException();

        verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.verify("nobody@gmail.com", "123456"))
                .hasMessage("Login code is invalid or has expired");
    }

    @Test
    void cooldownAppliesToEveryEmailSoItDoesNotRevealAccounts() {
        service.sendCode(EMAIL);
        service.sendCode("nobody@gmail.com");

        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> service.sendCode(EMAIL)).hasMessage("Please wait before requesting a new code");
        assertThatThrownBy(() -> service.sendCode("nobody@gmail.com"))
                .hasMessage("Please wait before requesting a new code");

        clock.advance(Duration.ofSeconds(2));
        assertThatCode(() -> service.sendCode(EMAIL)).doesNotThrowAnyException();
        verify(mailService, times(2)).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void newCodeInvalidatesPreviousOne() {
        service.sendCode(EMAIL);
        String first = lastMailedCode();
        clock.advance(Duration.ofSeconds(61));
        clearInvocations(mailService);
        service.sendCode(EMAIL);
        String second = lastMailedCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> service.verify(EMAIL, first)).hasMessage("Login code is incorrect");
        }
        assertThat(service.verify(EMAIL, second).userId()).isEqualTo(5L);
    }

    @Test
    void codeExpiresAfterFiveMinutes() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();

        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.verify(EMAIL, code)).hasMessage("Login code is invalid or has expired");
        verify(authService, never()).issueToken(any());
    }

    @Test
    void codeStillWorksJustBeforeExpiry() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));

        assertThat(service.verify(EMAIL, code).userId()).isEqualTo(5L);
    }

    @Test
    void codeIsDiscardedAfterFiveWrongAttempts() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < LoginOtpService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verify(EMAIL, wrong)).hasMessage("Login code is incorrect");
        }
        assertThatThrownBy(() -> service.verify(EMAIL, wrong))
                .hasMessage("Too many wrong codes, please request a new code");
        // mã đúng cũng không dùng được nữa
        assertThatThrownBy(() -> service.verify(EMAIL, code)).hasMessage("Login code is invalid or has expired");
        verify(authService, never()).issueToken(any());
    }

    @Test
    void correctCodeStillWorksAfterFewerThanFiveWrongAttempts() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < LoginOtpService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verify(EMAIL, wrong)).hasMessage("Login code is incorrect");
        }
        assertThat(service.verify(EMAIL, code).userId()).isEqualTo(5L);
    }

    @Test
    void codeCanBeUsedOnlyOnce() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();

        service.verify(EMAIL, code);

        assertThatThrownBy(() -> service.verify(EMAIL, code)).hasMessage("Login code is invalid or has expired");
        verify(authService, times(1)).issueToken(any());
    }

    @Test
    void accountLockedAfterCodeWasSentIsRejected() {
        service.sendCode(EMAIL);
        String code = lastMailedCode();
        customer.setEnabled(false);

        assertThatThrownBy(() -> service.verify(EMAIL, code))
                .isInstanceOf(DisabledException.class)
                .hasMessage("Account is locked");
        verify(authService, never()).issueToken(any());
    }

    @Test
    void reportsMailNotConfiguredForEveryEmail() {
        doThrow(new MailNotConfiguredException()).when(mailService).ensureCanSend();

        assertThatThrownBy(() -> service.sendCode(EMAIL)).isInstanceOf(MailNotConfiguredException.class);
        assertThatThrownBy(() -> service.sendCode("nobody@gmail.com")).isInstanceOf(MailNotConfiguredException.class);
    }

    private String lastMailedCode() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), anyString(), text.capture());
        Matcher m = CODE.matcher(text.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
