package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 客户购买历史条目（卖家视角）：含失败原因，商品 ID 供跳转历史商品详情。
 */
public record CustomerPurchaseEntryData(
        @JsonProperty("goods_id") String goodsId,
        @JsonProperty("goods_name") String goodsName,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("result") String result,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("finish_at") String finishAt) {
}
