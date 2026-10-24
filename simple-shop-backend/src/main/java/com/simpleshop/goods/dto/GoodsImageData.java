package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 商品图片。主图至多一张，sort_no 从 1 开始。
 */
public record GoodsImageData(
        @JsonProperty("url") String url,
        @JsonProperty("is_main") boolean main,
        @JsonProperty("sort_no") int sortNo) {
}
