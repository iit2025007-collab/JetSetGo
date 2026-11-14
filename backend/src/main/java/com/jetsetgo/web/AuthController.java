package com.jetsetgo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthService.AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 10, max = 72) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
}
