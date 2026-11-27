package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 支付动作的返回。只回这次动作改变的东西，订单详情仍由详情接口取。
 *
 * <p>{@code redirectUrl} 只有真实渠道重定向（PENDING）时才有值；Mock 恒成功，
 * 所以本期它一直是 null。字段先留着，前端按「有值就跳转」写，接网关时不用改契约。
 */
public record OrderPaymentData(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("payment_channel") PaymentChannel paymentChannel,
        @JsonProperty("paid_at") String paidAt,
        @JsonProperty("redirect_url") String redirectUrl) {
}
