package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;

/**
 * 单个类别。一级类别的 parent_id 为 null。
 */
public record CategoryData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("parent_id") String parentId) {

    public static CategoryData of(CategoryL1 category) {
        return new CategoryData(category.getId(), category.getName(), null);
    }

    public static CategoryData of(CategoryL2 category) {
        return new CategoryData(category.getId(), category.getName(), category.getL1().getId());
    }
}
