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
public class FacebookEmailLoginService {
    private final FacebookTokenVerifier verifier;
    private final FacebookEmailCodes codes;
    private final UserRepository users;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;

    public FacebookEmailLoginService(FacebookTokenVerifier verifier, FacebookEmailCodes codes, UserRepository users,
                                    AuthService auth, PasswordEncoder passwords, PlatformTransactionManager manager) {
        this.verifier = verifier; this.codes = codes; this.users = users; this.auth = auth; this.passwords = passwords;
        this.tx = new TransactionTemplate(manager);
    }

    public void sendCode(String token, String email) {
        var profile = verifier.verify(token);
        codes.send(profile.subject(), EmailUtils.normalize(email));
    }

    public LoginResponse complete(String token, String email, String code) {
        var profile = verifier.verify(token);
        String normalized = EmailUtils.normalize(email);
        codes.verify(profile.subject(), normalized, code);
        return tx.execute(status -> {
            users.lockIdByEmail(normalized);
            User linked = users.findByFacebookSubject(profile.subject()).orElse(null);
            if (linked != null) {
                if (!linked.getEmail().equals(normalized)) throw conflict();
                if (!linked.isEnabled()) throw new DisabledException("Account is locked");
                return auth.issueToken(linked);
            }
            User user = users.findByEmail(normalized).orElse(null);
            if (user != null) {
                if (!user.isEnabled()) throw new DisabledException("Account is locked");
                if (!(user instanceof Customer) || user.getFacebookSubject() != null) throw conflict();
                // Both the Facebook identity and mailbox have now been proven. Never replace an existing link.
                user.setFacebookSubject(profile.subject());
            } else {
                byte[] secret = new byte[32];
                new SecureRandom().nextBytes(secret);
                String name = profile.name().isBlank() ? normalized.substring(0, normalized.indexOf('@')) : profile.name().trim();
                user = auth.createCustomer(Customer.builder().email(normalized).facebookSubject(profile.subject())
                        .fullName(name.substring(0, Math.min(100, name.length())))
                        .password(passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret))).build());
            }
            return auth.issueToken(user);
        });
    }

    private static FacebookSignInException conflict() {
        return new FacebookSignInException("Facebook account cannot be linked to this email");
    }
}
