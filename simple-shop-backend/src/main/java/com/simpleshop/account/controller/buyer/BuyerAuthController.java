package com.simpleshop.account.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.account.dto.AuthTokenData;
import com.simpleshop.account.dto.BuyerLoginRequest;
import com.simpleshop.account.dto.BuyerRegisterRequest;
import com.simpleshop.account.service.BuyerAuthService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.ClientIp;
import com.simpleshop.common.auth.AuthInterceptor;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.common.text.UtcIso8601;

/**
 * 买家注册、登录、退出。
 */
@RestController
@RequestMapping("/api/buyer/auth")
@Tag(name = "买家认证")
public class BuyerAuthController {

    private final BuyerAuthService authService;
    private final SessionStore sessions;

    public BuyerAuthController(BuyerAuthService authService, SessionStore sessions) {
        this.authService = authService;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @Operation(summary = "买家注册")
    public ApiResponse<Void> register(
            @RequestBody BuyerRegisterRequest request, HttpServletRequest httpRequest) {
        authService.register(request, ClientIp.of(httpRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/session")
    @Operation(summary = "买家登录")
    public ApiResponse<AuthTokenData> login(
            @RequestBody BuyerLoginRequest request, HttpServletRequest httpRequest) {
        SessionStore.Session session = authService.login(request, ClientIp.of(httpRequest));
        return ApiResponse.ok(token(session));
    }

    @DeleteMapping("/session")
    @Operation(summary = "买家退出；无令牌或令牌无效时也返回成功")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        String token = AuthInterceptor.extractToken(httpRequest);
        if (token != null) {
            sessions.remove(token);
        }
        return ApiResponse.ok();
    }

    private AuthTokenData token(SessionStore.Session session) {
        return new AuthTokenData(session.token(), UtcIso8601.ofInstant(session.expireAt()));
    }
}
