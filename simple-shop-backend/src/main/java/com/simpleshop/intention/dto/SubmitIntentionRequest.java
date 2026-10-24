package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 提交购买意向请求。联系方式取买家账号当前值，不随请求提交。
 */
public record SubmitIntentionRequest(
        @JsonProperty("goods_id") String goodsId) {
}
