package com.susuggang.controller;

import com.susuggang.dto.LoginRequest;
import com.susuggang.dto.SignupRequest;
import com.susuggang.dto.TokenResponse;
import com.susuggang.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public Long signup(@RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return new TokenResponse(authService.login(request.email(), request.password()));
    }
}
