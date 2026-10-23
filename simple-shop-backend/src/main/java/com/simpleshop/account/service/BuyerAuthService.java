package com.simpleshop.account.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.dto.BuyerLoginRequest;
import com.simpleshop.account.dto.BuyerRegisterRequest;
import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.repository.BuyerAccountRepository;
import com.simpleshop.account.support.AccountRules;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.common.security.RateLimiter;

/**
 * 买家注册与登录。
 */
@Service
public class BuyerAuthService {

    private final BuyerAccountRepository buyers;
    private final SessionStore sessions;
    private final RateLimiter rateLimiter;

    public BuyerAuthService(
            BuyerAccountRepository buyers, SessionStore sessions, RateLimiter rateLimiter) {
        this.buyers = buyers;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void register(BuyerRegisterRequest request, String clientKey) {
        requireRateLimit(RateLimiter.Scope.REGISTER, clientKey);
        String username = AccountRules.username(request.username());
        String password = AccountRules.newPassword(request.password());
        String phone = AccountRules.phone(request.phone());
        String location = AccountRules.defaultLocation(request.defaultLocation());

        if (buyers.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
        try {
            buyers.saveAndFlush(BuyerAccount.register(
                    username, PasswordHasher.hash(password), phone, location));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public SessionStore.Session login(BuyerLoginRequest request, String clientKey) {
        requireRateLimit(RateLimiter.Scope.LOGIN, clientKey);
        String username = AccountRules.username(request.username());
        String password = AccountRules.loginPassword(request.password());

        BuyerAccount buyer = buyers.findByUsername(username).orElse(null);
        if (buyer == null || !PasswordHasher.matches(password, buyer.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return sessions.create(new AuthPrincipal(
                AuthRole.BUYER, buyer.getId(), buyer.getUsername()));
    }

    private void requireRateLimit(RateLimiter.Scope scope, String clientKey) {
        if (!rateLimiter.tryAcquire(scope, clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
