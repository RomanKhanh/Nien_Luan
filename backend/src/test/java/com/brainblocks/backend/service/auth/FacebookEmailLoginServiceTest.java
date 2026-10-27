package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.exception.FacebookSignInException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FacebookEmailLoginServiceTest {
    private FacebookTokenVerifier verifier;
    private FacebookEmailCodes codes;
    private UserRepository users;
    private AuthService auth;
    private FacebookEmailLoginService service;
    @BeforeEach void setup() {
        verifier = mock(FacebookTokenVerifier.class); codes = mock(FacebookEmailCodes.class);
        users = mock(UserRepository.class); auth = mock(AuthService.class);
        var passwords = mock(PasswordEncoder.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(passwords.encode(anyString())).thenReturn("hash");
        when(verifier.verify("token")).thenReturn(new FacebookTokenVerifier.Profile("42", "Bao", ""));
        when(auth.createCustomer(any())).thenAnswer(call -> call.getArgument(0));
        service = new FacebookEmailLoginService(verifier, codes, users, auth, passwords, manager);
    }
    @Test void requiresBothProofsBeforeLinkingExistingCustomer() {
        var customer = Customer.builder().email("a@example.com").enabled(true).build();
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(customer));
        service.complete("token", " A@Example.com ", "123456");
        var order = inOrder(verifier, codes, users, auth);
        order.verify(verifier).verify("token");
        order.verify(codes).verify("42", "a@example.com", "123456");
        order.verify(users).lockIdByEmail("a@example.com");
        order.verify(users).findByFacebookSubject("42");
        order.verify(users).findByEmail("a@example.com");
        order.verify(auth).issueToken(customer);
        assertThat(customer.getFacebookSubject()).isEqualTo("42");
        verify(auth, never()).createCustomer(any());
    }
    @Test void invalidOtpDoesNotTouchDatabase() {
        doThrow(new IllegalArgumentException("incorrect")).when(codes).verify(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.complete("token", "a@example.com", "123456")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, auth);
    }
    @Test void invalidFacebookTokenCannotSendEmail() {
        when(verifier.verify("bad")).thenThrow(new FacebookSignInException("invalid"));
        assertThatThrownBy(() -> service.sendCode("bad", "a@example.com")).isInstanceOf(FacebookSignInException.class);
        verifyNoInteractions(codes, users, auth);
    }
    @Test void createsCustomerWithVerifiedMailbox() {
        service.complete("token", "a@example.com", "123456");
        verify(auth).createCustomer(argThat(c -> c.getEmail().equals("a@example.com") && c.getFacebookSubject().equals("42") && c.getPassword().equals("hash")));
    }
    @Test void cannotLinkAdminOrReplaceAnotherFacebookIdentity() {
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(Admin.builder().enabled(true).build()));
        assertThatThrownBy(() -> service.complete("token", "a@example.com", "123456")).isInstanceOf(FacebookSignInException.class);
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(Customer.builder().enabled(true).facebookSubject("other").build()));
        assertThatThrownBy(() -> service.complete("token", "a@example.com", "123456")).isInstanceOf(FacebookSignInException.class);
        verifyNoInteractions(auth);
    }
    @Test void cannotLinkLockedCustomer() {
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(Customer.builder().enabled(false).build()));
        assertThatThrownBy(() -> service.complete("token", "a@example.com", "123456")).isInstanceOf(DisabledException.class);
        verifyNoInteractions(auth);
    }
    @Test void cannotMoveFacebookIdentityToDifferentMailbox() {
        when(users.findByFacebookSubject("42")).thenReturn(Optional.of(Customer.builder().email("other@example.com").enabled(true).build()));
        assertThatThrownBy(() -> service.complete("token", "a@example.com", "123456")).isInstanceOf(FacebookSignInException.class);
        verifyNoInteractions(auth);
    }
}
