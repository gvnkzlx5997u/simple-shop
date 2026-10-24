package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 交易裁决结果。goods_status 归档时为 ARCHIVED，其余为商品当前状态枚举名。
 */
public record TradeResultData(
        @JsonProperty("intention") IntentionEntryData intention,
        @JsonProperty("goods_status") String goodsStatus) {
}
