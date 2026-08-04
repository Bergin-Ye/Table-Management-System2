package com.erp.controller;

import com.erp.common.ApiResponse;
import com.erp.dto.LoginRequest;
import com.erp.dto.LoginResponse;
import com.erp.security.LoginUser;
import com.erp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @GetMapping("/userinfo")
    public ApiResponse<LoginUser> userinfo() {
        return ApiResponse.ok(authService.currentUserInfo());
    }
}
