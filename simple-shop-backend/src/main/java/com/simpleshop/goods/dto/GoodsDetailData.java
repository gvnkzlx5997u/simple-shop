package com.simpleshop.goods.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 商品详情。purchasable 仅表示当前是否允许提交意向，S3 不实现提交。
 */
public record GoodsDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("category") CategoryRefData category,
        @JsonProperty("main_image_url") String mainImageUrl,
        @JsonProperty("images") List<GoodsImageData> images,
        @JsonProperty("description") List<DescriptionBlockData> description,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt,
        @JsonProperty("purchasable") boolean purchasable) {

    public GoodsDetailData {
        images = images == null ? List.of() : List.copyOf(images);
        description = description == null ? List.of() : List.copyOf(description);
    }
}
