package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发布商品时的描述内容块。顺序即展示顺序。
 *
 * <p>type 与 text/url 的组合规则（TEXT 只带文本、IMAGE/VIDEO 只带 URL）依赖内容，
 * 由 {@code GoodsPublishPreparer} 判定。
 */
public record DescriptionBlockRequest(
        @JsonProperty("type")
        @NotBlank(message = "must not be blank")
        @Size(max = 16, message = "must be at most 16 characters")
        String type,

        @JsonProperty("text")
        String text,

        @JsonProperty("url")
        @Size(max = 512, message = "must be at most 512 characters")
        String url) {
}
