package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 新增二级类别。
 */
public record CreateCategoryL2Request(
        @JsonProperty("l1_id") String l1Id,
        @JsonProperty("name") String name) {
}
