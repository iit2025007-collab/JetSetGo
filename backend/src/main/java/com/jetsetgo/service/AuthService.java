package com.jetsetgo.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.config.JwtService;
import com.jetsetgo.domain.AppUser;
import com.jetsetgo.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    public AuthResult register(String name, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(null, normalizedEmail,
                passwords.encode(password), name.trim(), AppUser.Role.CUSTOMER));
        return result(user);
    }

    public AuthResult login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .filter(account -> passwords.matches(password, account.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Email or password is incorrect. Check your details and try again."));
        return result(user);
    }

    private AuthResult result(AppUser user) {
        return new AuthResult(tokens.issue(user), new UserView(user.id(), user.name(), user.email(), user.role()));
    }

    public record AuthResult(String token, UserView user) {}
    public record UserView(String id, String name, String email, AppUser.Role role) {}
}
