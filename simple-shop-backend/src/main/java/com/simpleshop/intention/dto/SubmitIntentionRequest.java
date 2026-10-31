package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交购买意向请求。联系方式取买家账号当前值，不随请求提交。
 */
public record SubmitIntentionRequest(
        @JsonProperty("goods_id")
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String goodsId) {
}
