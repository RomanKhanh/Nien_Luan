package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.exception.FacebookSignInException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FacebookLoginServiceTest {
    private FacebookTokenVerifier verifier;
    private UserRepository users;
    private AuthService auth;
    private FacebookLoginService service;

    @BeforeEach void setup() {
        verifier = mock(FacebookTokenVerifier.class);
        users = mock(UserRepository.class);
        auth = mock(AuthService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(passwords.encode(anyString())).thenReturn("hashed-secret");
        when(verifier.verify("token")).thenReturn(new FacebookTokenVerifier.Profile("42", "Bao", "BAO@example.com"));
        when(users.findByFacebookSubject("42")).thenReturn(Optional.empty());
        when(auth.createCustomer(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new FacebookLoginService(verifier, users, auth, passwords, manager);
    }

    @Test void createsCustomerThroughExistingCartAndVoucherFlow() {
        service.login("token");
        var customer = ArgumentCaptor.forClass(Customer.class);
        verify(auth).createCustomer(customer.capture());
        assertThat(customer.getValue().getEmail()).isEqualTo("bao@example.com");
        assertThat(customer.getValue().getFacebookSubject()).isEqualTo("42");
        assertThat(customer.getValue().getPassword()).isEqualTo("hashed-secret");
        assertThat(customer.getValue().getRole().name()).isEqualTo("CUSTOMER");
        verify(auth).issueToken(customer.getValue());
    }

    @Test void existingIdentityDoesNotNeedEmailOrReceiveDuplicateWelcomeBenefits() {
        Customer customer = Customer.builder().facebookSubject("42").enabled(true).build();
        when(users.findByFacebookSubject("42")).thenReturn(Optional.of(customer));
        when(verifier.verify("token")).thenReturn(new FacebookTokenVerifier.Profile("42", "Bao", ""));
        service.login("token");
        verify(auth).issueToken(customer);
        verify(auth, never()).createCustomer(any());
    }

    @Test void lockedAccountCannotLogin() {
        when(users.findByFacebookSubject("42")).thenReturn(Optional.of(Customer.builder().enabled(false).build()));
        assertThatThrownBy(() -> service.login("token")).isInstanceOf(DisabledException.class);
        verifyNoInteractions(auth);
    }

    @Test void doesNotLinkAnExistingEmail() {
        when(users.existsByEmail("bao@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.login("token")).isInstanceOf(FacebookSignInException.class)
                .hasMessageContaining("already registered");
        verifyNoInteractions(auth);
    }

    @Test void newAccountNeedsEmail() {
        when(verifier.verify("token")).thenReturn(new FacebookTokenVerifier.Profile("42", "Bao", ""));
        assertThatThrownBy(() -> service.login("token")).isInstanceOf(FacebookSignInException.class)
                .hasMessage("Facebook email permission could not be determined");
        verifyNoInteractions(auth);
    }

    @Test void missingEmailReturnsSpecificPermissionProblemWithoutCreatingAnAccount() {
        String[][] cases = {
                {"NOT_GRANTED", "Facebook email permission not granted"},
                {"DECLINED", "Facebook email permission declined"},
                {"EXPIRED", "Facebook email permission expired"},
                {"GRANTED", "Facebook email missing despite granted permission"}
        };
        for (String[] entry : cases) {
            when(verifier.verify("token")).thenReturn(new FacebookTokenVerifier.Profile("42", "Bao", "",
                    FacebookTokenVerifier.EmailPermission.valueOf(entry[0])));
            assertThatThrownBy(() -> service.login("token")).isInstanceOf(FacebookSignInException.class)
                    .hasMessage(entry[1]);
        }
        verifyNoInteractions(auth);
    }

    @Test void invalidTokenNeverTouchesDatabase() {
        when(verifier.verify("token")).thenThrow(new FacebookSignInException("Invalid Facebook credential"));
        assertThatThrownBy(() -> service.login("token")).isInstanceOf(FacebookSignInException.class);
        verifyNoInteractions(users, auth);
    }
}
