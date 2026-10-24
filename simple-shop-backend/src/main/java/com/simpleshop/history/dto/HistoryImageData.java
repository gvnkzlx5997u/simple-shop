package com.simpleshop.history.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史图片快照：形状与归档时的商品图片一致。
 */
public record HistoryImageData(
        @JsonProperty("url") String url,
        @JsonProperty("is_main") boolean main,
        @JsonProperty("sort_no") int sortNo) {
}
