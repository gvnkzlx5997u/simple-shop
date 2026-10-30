package com.simpleshop.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.simpleshop.account.dto.ChangePasswordRequest;
import com.simpleshop.account.entity.SellerAccount;
import com.simpleshop.account.repository.SellerAccountRepository;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.common.security.RateLimiter;

class SellerAuthServiceImplTest {

    private final SellerAccountRepository sellers = mock(SellerAccountRepository.class);
    private final SessionStore sessions =
            SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ofSeconds(60));
    private final SellerAuthServiceImpl service = new SellerAuthServiceImpl(
            sellers, sessions, mock(RateLimiter.class), mock(OperationLogService.class));

    @AfterEach
    void cleanup() {
        sessions.removeAllFor(AuthRole.SELLER);
        reset();
    }

    @Test
    void revokesSessionsOnlyAfterCommit() {
        SellerAccount seller = seller();
        AuthPrincipal principal = new AuthPrincipal(AuthRole.SELLER, seller.getId(), "seller");
        String token = sessions.create(principal).token();

        begin();
        service.changePassword(principal, new ChangePasswordRequest("Abcd@1234", "NewPass@1234"));

        assertThat(sessions.validate(token, AuthRole.SELLER)).isPresent();
        commit();
        assertThat(sessions.validate(token, AuthRole.SELLER)).isEmpty();
    }

    @Test
    void keepsSessionsWhenTransactionRollsBack() {
        SellerAccount seller = seller();
        AuthPrincipal principal = new AuthPrincipal(AuthRole.SELLER, seller.getId(), "seller");
        String token = sessions.create(principal).token();

        begin();
        service.changePassword(principal, new ChangePasswordRequest("Abcd@1234", "NewPass@1234"));
        rollback();

        assertThat(sessions.validate(token, AuthRole.SELLER)).isPresent();
    }

    private SellerAccount seller() {
        SellerAccount seller = SellerAccount.create("seller", PasswordHasher.hash("Abcd@1234"));
        when(sellers.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(sellers.saveAndFlush(seller)).thenReturn(seller);
        return seller;
    }

    private static void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        reset();
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        reset();
    }

    private static void reset() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
