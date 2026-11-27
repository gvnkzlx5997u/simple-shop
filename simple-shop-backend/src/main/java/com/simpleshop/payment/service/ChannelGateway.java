package com.simpleshop.payment.service;

import java.time.LocalDateTime;

import com.simpleshop.payment.api.PaymentChannel;
import com.simpleshop.payment.api.PaymentRequest;
import com.simpleshop.payment.api.PaymentResult;
import com.simpleshop.payment.api.RefundRequest;

/**
 * 渠道适配器：一个实现对应一类接入方式（Mock、支付宝沙箱、微信……）。
 *
 * <p>适配器只做「翻译」：把统一的请求翻成渠道调用，把渠道结果翻回
 * {@link PaymentResult}；不读写流水、不判断订单。流水由
 * {@link PaymentService} 统一记，换渠道不影响账。
 *
 * <p>退款返回退款时间而不是回执：流水 ID 由本地生成，渠道只需要告诉服务层
 * 「钱是什么时候退的」。真实渠道的异步退款会在扩展时改成带状态的结果。
 */
public interface ChannelGateway {

    boolean supports(PaymentChannel channel);

    PaymentResult charge(PaymentRequest request);

    LocalDateTime refund(RefundRequest request);
}
