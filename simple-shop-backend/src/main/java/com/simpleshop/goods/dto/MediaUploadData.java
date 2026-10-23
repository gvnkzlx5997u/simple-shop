package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 媒体上传结果。
 */
public record MediaUploadData(
        @JsonProperty("url") String url,
        @JsonProperty("kind") String kind,
        @JsonProperty("size_bytes") long sizeBytes) {
}
