package com.simpleshop.common.tx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @AfterEach
    void clearTransactionState() {
        reset();
    }

    @Test
    void runsImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean();

        AfterCommit.run(() -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void defersUntilCommit() {
        begin();
        AtomicBoolean ran = new AtomicBoolean();

        AfterCommit.run(() -> ran.set(true));

        assertThat(ran).isFalse();
        commit();
        assertThat(ran).isTrue();
    }

    @Test
    void skipsOnRollback() {
        begin();
        AtomicBoolean ran = new AtomicBoolean();

        AfterCommit.run(() -> ran.set(true));
        rollback();

        assertThat(ran).isFalse();
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
