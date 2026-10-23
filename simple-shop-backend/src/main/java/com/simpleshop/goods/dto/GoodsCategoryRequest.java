package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 商品改挂二级类别。
 */
public record GoodsCategoryRequest(@JsonProperty("category_id") String categoryId) {
}
