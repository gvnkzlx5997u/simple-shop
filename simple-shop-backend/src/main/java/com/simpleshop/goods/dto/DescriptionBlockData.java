package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 商品描述内容块。TEXT 块有 text，IMAGE/VIDEO 块有 url。
 */
public record DescriptionBlockData(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text,
        @JsonProperty("url") String url) {
}
