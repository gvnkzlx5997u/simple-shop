package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 修改库存。提交 0 会触发库存清零下架。
 */
public record GoodsStockRequest(@JsonProperty("stock") Integer stock) {
}
