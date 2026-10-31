package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 修改库存。提交 0 会触发库存清零下架。
 */
public record GoodsStockRequest(
        @JsonProperty("stock")
        @NotNull(message = "must not be null")
        @Min(value = 0, message = "must be at least 0")
        Integer stock) {
}
