package com.simpleshop.goods.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发布商品。图片需先通过 /api/seller/media 上传，再提交返回的 URL。
 *
 * <p>边界校验只覆盖存在性、上下界与长度；尖括号、换行、金额精度等语义规则
 * 仍由 {@code GoodsRules} 判定，保证错误码可区分。
 */
public record GoodsPublishRequest(
        @JsonProperty("name")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String name,

        @JsonProperty("price")
        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @DecimalMax(value = "999999.99", message = "must be at most 999999.99")
        BigDecimal price,

        @JsonProperty("stock")
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        Integer stock,

        @JsonProperty("category_id")
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String categoryId,

        @JsonProperty("description")
        @Valid
        List<DescriptionBlockRequest> description,

        @JsonProperty("main_image")
        @Size(max = 512, message = "must be at most 512 characters")
        String mainImage,

        @JsonProperty("gallery_images")
        List<@Size(max = 512, message = "must be at most 512 characters") String> galleryImages) {
}
