package com.simpleshop.account.service;

import com.simpleshop.account.dto.ChangePasswordRequest;
import com.simpleshop.account.dto.SellerLoginRequest;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.SessionStore;

/**
 * 卖家登录与改密。
 */
public interface SellerAuthService {

    SessionStore.Session login(SellerLoginRequest request, String clientKey);

    void changePassword(AuthPrincipal principal, ChangePasswordRequest request);
}
