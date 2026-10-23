package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 新增一级类别。
 */
public record CreateCategoryL1Request(@JsonProperty("name") String name) {
}
