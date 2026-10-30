package com.simpleshop.account.service.impl;

import com.simpleshop.account.service.SellerAuthService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.dto.ChangePasswordRequest;
import com.simpleshop.account.dto.SellerLoginRequest;
import com.simpleshop.account.entity.SellerAccount;
import com.simpleshop.account.repository.SellerAccountRepository;
import com.simpleshop.account.support.AccountRules;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.common.security.RateLimiter;
import com.simpleshop.common.tx.AfterCommit;

/**
 * 卖家登录与改密。
 */
@Service
public class SellerAuthServiceImpl implements SellerAuthService {

    private final SellerAccountRepository sellers;
    private final SessionStore sessions;
    private final RateLimiter rateLimiter;
    private final OperationLogService audit;

    public SellerAuthServiceImpl(
            SellerAccountRepository sellers,
            SessionStore sessions,
            RateLimiter rateLimiter,
            OperationLogService audit) {
        this.sellers = sellers;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SessionStore.Session login(SellerLoginRequest request, String clientKey) {
        requireRateLimit(clientKey);
        String account = AccountRules.account(request.account());
        String password = AccountRules.loginPassword(request.password());

        SellerAccount seller = sellers.findByAccount(account).orElse(null);
        if (seller == null || !PasswordHasher.matches(password, seller.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return sessions.create(new AuthPrincipal(
                AuthRole.SELLER, seller.getId(), seller.getAccount()));
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        SellerAccount seller = sellers.findById(principal.subjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));
        if (request.oldPassword() == null
                || request.oldPassword().isBlank()
                || !PasswordHasher.matches(request.oldPassword(), seller.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        String newPassword = AccountRules.newPassword(request.newPassword());
        seller.changePassword(PasswordHasher.hash(newPassword));
        sellers.saveAndFlush(seller);
        // 事务回滚时不能提前踢掉会话，否则改密失败但用户被迫重新登录。
        AfterCommit.run(() -> sessions.removeAllFor(AuthRole.SELLER, seller.getId()));
        audit.log(OperationLogService.CHANGE_PASSWORD,
                OperationLogService.TARGET_ACCOUNT, seller.getId());
    }

    private void requireRateLimit(String clientKey) {
        if (!rateLimiter.tryAcquire(RateLimiter.Scope.LOGIN, clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
