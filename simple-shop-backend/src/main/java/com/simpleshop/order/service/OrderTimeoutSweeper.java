package com.simpleshop.order.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.simpleshop.common.config.AppProperties;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.support.PaymentDeadline;

/**
 * 超时扫描：定时把到期未支付的订单交给 {@link OrderTimeoutService} 逐笔取消。
 *
 * <p>扫描只做两件事：查出到期订单 ID、逐笔调用。每笔一个事务，某笔失败不影响整批；
 * 启动即扫一次，补上停机期间到期的订单。不做分布式锁——项目是单副本部署。
 */
@Component
public class OrderTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutSweeper.class);

    private final OrderRepository orders;
    private final OrderTimeoutService timeouts;
    private final PaymentDeadline deadlines;
    private final AppProperties properties;

    public OrderTimeoutSweeper(
            OrderRepository orders,
            OrderTimeoutService timeouts,
            PaymentDeadline deadlines,
            AppProperties properties) {
        this.orders = orders;
        this.timeouts = timeouts;
        this.deadlines = deadlines;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${simple-shop.order.timeout-scan-interval-ms:60000}",
            initialDelayString = "${simple-shop.order.timeout-initial-delay-ms:0}")
    public void sweep() {
        List<String> expired = orders.findIdsByStatusAndCreateAtBefore(
                OrderStatus.PENDING_PAYMENT,
                deadlines.expiredBefore(),
                PageRequest.of(0, properties.getOrder().getTimeoutBatchSize()));

        int canceled = 0;
        for (String orderId : expired) {
            try {
                if (timeouts.cancelExpired(orderId)) {
                    canceled++;
                }
            } catch (RuntimeException exception) {
                // 单笔失败只记日志，下一轮重扫；不要把整批拖垮。
                log.warn("timeout cancel failed: orderId={} reason={}",
                        orderId, exception.toString());
            }
        }
        if (canceled > 0) {
            log.info("timeout sweep canceled {} of {} expired orders", canceled, expired.size());
        }
    }
}
