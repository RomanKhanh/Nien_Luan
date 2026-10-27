package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.FacebookSignInException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.util.EmailUtils;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class FacebookLoginService {
    private final FacebookTokenVerifier verifier;
    private final UserRepository users;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;

    public FacebookLoginService(FacebookTokenVerifier verifier, UserRepository users, AuthService auth,
                                PasswordEncoder passwords, PlatformTransactionManager manager) {
        this.verifier = verifier;
        this.users = users;
        this.auth = auth;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(manager);
    }

    public LoginResponse login(String accessToken) {
        // Network requests run outside the database transaction.
        var profile = verifier.verify(accessToken);
        return transaction.execute(status -> loginVerified(profile));
    }

    private LoginResponse loginVerified(FacebookTokenVerifier.Profile profile) {
        User user = users.findByFacebookSubject(profile.subject()).orElse(null);
        if (user != null) {
            if (!user.isEnabled()) throw new DisabledException("Account is locked");
            return auth.issueToken(user);
        }
        String email = EmailUtils.normalize(profile.email());
        if (email == null || email.isBlank() || !email.contains("@") || email.length() > 255) {
            throw new FacebookSignInException(switch (profile.emailPermission()) {
                case NOT_GRANTED -> "Facebook email permission not granted";
                case DECLINED -> "Facebook email permission declined";
                case EXPIRED -> "Facebook email permission expired";
                case GRANTED -> "Facebook email missing despite granted permission";
                case UNKNOWN -> "Facebook email permission could not be determined";
            });
        }
        // Facebook has no email_verified claim equivalent to Google. Never silently link existing accounts.
        if (users.existsByEmail(email)) throw new FacebookSignInException("Facebook email already registered; use another sign-in method");
        String name = profile.name().isBlank() ? email.substring(0, email.indexOf('@')) : profile.name().trim();
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        user = auth.createCustomer(Customer.builder().email(email).fullName(name.substring(0, Math.min(100, name.length())))
                .facebookSubject(profile.subject())
                .password(passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret))).build());
        return auth.issueToken(user);
    }
}
