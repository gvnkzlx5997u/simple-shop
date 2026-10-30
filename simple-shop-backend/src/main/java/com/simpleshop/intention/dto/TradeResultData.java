package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.intention.enums.TradeGoodsStatus;

/**
 * 交易裁决结果。goods_status 归档时为 ARCHIVED，其余为 ON_SALE。
 */
public record TradeResultData(
        @JsonProperty("intention") IntentionEntryData intention,
        @JsonProperty("goods_status") TradeGoodsStatus goodsStatus) {
}
