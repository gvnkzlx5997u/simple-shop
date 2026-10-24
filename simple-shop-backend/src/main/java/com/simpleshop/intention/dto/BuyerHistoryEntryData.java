package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 买家历史条目。只含商品名、下单时间、结果与交易时间；
 * 不含价格、图片、卖家信息与排队信息。
 */
public record BuyerHistoryEntryData(
        @JsonProperty("goods_name") String goodsName,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("result") String result,
        @JsonProperty("finish_at") String finishAt) {
}
