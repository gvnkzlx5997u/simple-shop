package com.simpleshop.account.controller.seller;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.account.dto.AuthTokenData;
import com.simpleshop.account.dto.ChangePasswordRequest;
import com.simpleshop.account.dto.SellerLoginRequest;
import com.simpleshop.account.service.SellerAuthService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.ClientIp;
import com.simpleshop.common.auth.AuthInterceptor;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.CurrentUser;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.common.text.UtcIso8601;

/**
 * 卖家登录、退出、改密。
 */
@RestController
@RequestMapping("/api/seller/auth")
@Tag(name = "卖家认证")
public class SellerAuthController {

    private final SellerAuthService authService;
    private final SessionStore sessions;

    public SellerAuthController(SellerAuthService authService, SessionStore sessions) {
        this.authService = authService;
        this.sessions = sessions;
    }

    @PostMapping("/session")
    @Operation(summary = "卖家登录")
    public ApiResponse<AuthTokenData> login(
            @RequestBody SellerLoginRequest request, HttpServletRequest httpRequest) {
        SessionStore.Session session = authService.login(request, ClientIp.of(httpRequest));
        return ApiResponse.ok(token(session));
    }

    @DeleteMapping("/session")
    @Operation(summary = "卖家退出；无令牌或令牌无效时也返回成功")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        String token = AuthInterceptor.extractToken(httpRequest);
        if (token != null) {
            sessions.remove(token);
        }
        return ApiResponse.ok();
    }

    @PutMapping("/password")
    @Operation(summary = "卖家修改密码；成功后全部卖家会话失效")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> changePassword(
            @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require(httpRequest);
        authService.changePassword(principal, request);
        return ApiResponse.ok();
    }

    private AuthTokenData token(SessionStore.Session session) {
        return new AuthTokenData(session.token(), UtcIso8601.ofInstant(session.expireAt()));
    }
}
