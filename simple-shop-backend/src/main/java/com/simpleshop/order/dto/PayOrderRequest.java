package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;

/**
 * 支付请求。渠道用字符串收，再由服务层转枚举：这样非法渠道能落到业务码 83001，
 * 而不是被 Jackson 变成「参数格式错误」。
 */
public record PayOrderRequest(
        @JsonProperty("payment_channel")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 20)
        String paymentChannel) {
}
