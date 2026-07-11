package com.susuggang.controller;

import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.LoginRequest;
import com.susuggang.dto.SignupRequest;
import com.susuggang.dto.TokenResponse;
import com.susuggang.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public CommonResponse<Long> signup(@RequestBody SignupRequest request) {
        return CommonResponse.success(
                authService.signup(request.email(), request.password())
        );
    }

    @PostMapping("/login")
    public CommonResponse<TokenResponse> login(@RequestBody LoginRequest request) {
        return CommonResponse.success(
                new TokenResponse(authService.login(request.email(), request.password()))
        );
    }
}
