package com.simpleshop.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.payment.api.PaymentChannel;
import com.simpleshop.payment.api.PaymentPort;
import com.simpleshop.payment.api.PaymentRequest;
import com.simpleshop.payment.api.PaymentResult;
import com.simpleshop.payment.api.RefundReceipt;
import com.simpleshop.payment.api.RefundRequest;
import com.simpleshop.payment.entity.PaymentRefund;
import com.simpleshop.payment.repository.PaymentRefundRepository;

/**
 * 支付端口实现：按渠道选适配器，调用后统一记流水。
 *
 * <p>本类不判断订单状态、不读写订单表——「能不能付」是 order 的事。这里只做三件：
 * 找得到渠道、把结果翻成统一类型、把退款落成一条能独立看懂的账。
 *
 * <p>退款幂等走两层：先查同一订单同一原因是否已有流水，有就直接返回（正常重试路径）；
 * 订单侧在取消时还会锁订单行，两个并发取消会串行。数据库唯一键是最后一道保险，
 * 真撞上时按失败返回——事务已经不可继续，静默吞掉只会留下半截状态。
 */
@Service
public class PaymentService implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final List<ChannelGateway> gateways;
    private final PaymentRefundRepository refunds;

    public PaymentService(List<ChannelGateway> gateways, PaymentRefundRepository refunds) {
        this.gateways = List.copyOf(gateways);
        this.refunds = refunds;
        if (this.gateways.isEmpty()) {
            log.warn("no payment gateway available: mock is disabled and no real channel is wired, "
                    + "all payments will be rejected");
        }
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return gatewayFor(request.channel()).charge(request);
    }

    @Override
    @Transactional
    public RefundReceipt refund(RefundRequest request) {
        if (!PaymentRefund.REASON_ORDER_CANCEL.equals(request.reason())) {
            throw new IllegalArgumentException("unsupported refund reason: " + request.reason());
        }
        Optional<PaymentRefund> existing =
                refunds.findByOrderIdAndReason(request.orderId(), request.reason());
        if (existing.isPresent()) {
            return receiptOf(existing.get());
        }

        PaymentChannel channel = request.channel();
        LocalDateTime refundedAt = gatewayFor(channel).refund(request);
        PaymentRefund ledger = PaymentRefund.forOrderCancel(
                request.orderId(), request.orderNo(), channel, request.amount(), refundedAt);
        try {
            return receiptOf(refunds.saveAndFlush(ledger));
        } catch (DataIntegrityViolationException exception) {
            // 唯一键兜底：说明同一订单同一原因已经退过。订单侧有行锁，正常不会走到这里。
            log.warn("duplicate refund ledger rejected: orderId={} reason={}",
                    request.orderId(), request.reason());
            throw new BusinessException(ErrorCode.REFUND_FAILED,
                    "refund already recorded for order " + request.orderNo());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal refundedTotal(String orderId) {
        BigDecimal total = refunds.sumAmountByOrderId(orderId);
        return total == null ? BigDecimal.ZERO.setScale(2) : total;
    }

    private ChannelGateway gatewayFor(PaymentChannel channel) {
        return gateways.stream()
                .filter(gateway -> gateway.supports(channel))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("no gateway supports channel {}", channel);
                    return new BusinessException(ErrorCode.PAYMENT_FAILED,
                            "payment channel is not available: " + channel);
                });
    }

    private static RefundReceipt receiptOf(PaymentRefund refund) {
        return new RefundReceipt(refund.getId(), refund.getAmount(), refund.getRefundedAt());
    }
}
