package com.simpleshop.goods.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发布商品。图片需先通过 /api/seller/media 上传，再提交返回的 URL。
 */
public record GoodsPublishRequest(
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") Integer stock,
        @JsonProperty("category_id") String categoryId,
        @JsonProperty("description") List<DescriptionBlockRequest> description,
        @JsonProperty("main_image") String mainImage,
        @JsonProperty("gallery_images") List<String> galleryImages) {
}
