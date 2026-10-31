package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品改挂二级类别。
 */
public record GoodsCategoryRequest(
        @JsonProperty("category_id")
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String categoryId) {
}
