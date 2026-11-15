package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.config.JwtService;
import com.jetsetgo.domain.AppUser;
import com.jetsetgo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtService tokens;
    @InjectMocks AuthService service;

    @Test
    void registrationPersistsNormalizedEmailAndEncodedPassword() {
        when(users.existsByEmailIgnoreCase("traveler@example.com")).thenReturn(false);
        when(passwords.encode("correct-password")).thenReturn("bcrypt-hash");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokens.issue(any(AppUser.class))).thenReturn("session-token");

        service.register("  Traveler  ", " Traveler@Example.com ", "correct-password");

        ArgumentCaptor<AppUser> savedUser = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(savedUser.capture());
        assertEquals("traveler@example.com", savedUser.getValue().email());
        assertEquals("bcrypt-hash", savedUser.getValue().passwordHash());
        assertEquals("Traveler", savedUser.getValue().name());
        assertEquals(AppUser.Role.CUSTOMER, savedUser.getValue().role());
    }

    @Test
    void wrongPasswordReturnsClearGenericAuthenticationError() {
        AppUser user = new AppUser("user-1", "traveler@example.com", "bcrypt-hash",
                "Traveler", AppUser.Role.CUSTOMER);
        when(users.findByEmailIgnoreCase("traveler@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.login("traveler@example.com", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        assertEquals("Email or password is incorrect. Check your details and try again.", failure.getReason());
    }
}
