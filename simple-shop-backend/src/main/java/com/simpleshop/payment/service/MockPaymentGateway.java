package com.simpleshop.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.payment.api.PaymentChannel;
import com.simpleshop.payment.api.PaymentRequest;
import com.simpleshop.payment.api.PaymentResult;
import com.simpleshop.payment.api.RefundRequest;

/**
 * Mock 网关：四个渠道都当作支付成功，退款直接返回当前时间。
 *
 * <p>失败用配置注入（{@code simple-shop.payment.mock.fail-channels}）而不是请求参数：
 * 接口契约里不该出现「模拟失败」这种测试语义，否则前端与 E2E 会以为它是业务字段。
 *
 * <p>关闭 Mock（{@code mock.enabled=false}）时这个 Bean 不注册，支付服务会因为没有可用
 * 渠道而一律拒绝——这是刻意的：宁可全失败，也不要静默地假装成功。
 */
@Component
@ConditionalOnProperty(
        prefix = "simple-shop.payment.mock",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MockPaymentGateway implements ChannelGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final Set<PaymentChannel> failingChannels;

    public MockPaymentGateway(AppProperties properties) {
        this.failingChannels = parseChannels(properties.getPayment().getMock().getFailChannels());
        if (!failingChannels.isEmpty()) {
            log.warn("mock payment will fail for channels: {}", failingChannels);
        }
    }

    @Override
    public boolean supports(PaymentChannel channel) {
        return true;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        if (failingChannels.contains(request.channel())) {
            return PaymentResult.failed(request.channel());
        }
        return PaymentResult.succeeded(
                request.channel(),
                DatabaseTimeProvider.utcNow(),
                "MOCK-" + UUID.randomUUID());
    }

    @Override
    public LocalDateTime refund(RefundRequest request) {
        // Mock 的退款不注入失败：本期只需要证明「取消已支付订单会落一条流水」。
        return DatabaseTimeProvider.utcNow();
    }

    /** 配置解析：逗号分隔、大小写不敏感、忽略空项；写错渠道名直接启动失败，不静默忽略。 */
    static Set<PaymentChannel> parseChannels(List<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .map(PaymentChannel::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
