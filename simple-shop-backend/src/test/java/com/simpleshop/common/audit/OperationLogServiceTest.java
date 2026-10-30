package com.simpleshop.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OperationLogServiceTest {

    private final OperationLogService service = new OperationLogService();
    private final Logger auditLogger =
            (Logger) LoggerFactory.getLogger("com.simpleshop.audit");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        reset();
    }

    @Test
    void writesImmediatelyOutsideTransaction() {
        service.log(OperationLogService.PUBLISH_GOODS,
                OperationLogService.TARGET_GOODS, "G1");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("operation=PUBLISH_GOODS", "targetId=G1");
    }

    @Test
    void writesAfterCommitOnly() {
        begin();
        service.log(OperationLogService.UPDATE_STOCK,
                OperationLogService.TARGET_GOODS, "G2");

        assertThat(appender.list).isEmpty();
        commit();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("UPDATE_STOCK");
    }

    @Test
    void dropsRecordWhenTransactionRollsBack() {
        begin();
        service.log(OperationLogService.OFF_SHELF_GOODS,
                OperationLogService.TARGET_GOODS, "G3");
        rollback();

        assertThat(appender.list).isEmpty();
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
