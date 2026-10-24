package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发布商品时的描述内容块。顺序即展示顺序。
 */
public record DescriptionBlockRequest(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text,
        @JsonProperty("url") String url) {
}
