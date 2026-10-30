package com.simpleshop.common.tx;

import java.util.Objects;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把副作用推迟到事务提交之后执行。
 *
 * <p>存在活动事务时注册 {@link TransactionSynchronization#afterCommit()}，回滚不会执行；
 * 没有事务时立即执行，保持调用方语义不变。典型用途：审计日志、会话撤销等不可回滚的副作用。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
