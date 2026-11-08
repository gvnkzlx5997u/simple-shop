package com.simpleshop.goods.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 商品详情。purchasable 等价于「在售且有库存」，前端不自行推断可购买性。
 * 在售时 off_shelf_reason 与 off_shelf_at 均为 null。
 */
public record GoodsDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("off_shelf_reason") OffShelfReason offShelfReason,
        @JsonProperty("off_shelf_at") String offShelfAt,
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
