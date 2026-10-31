package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增一级类别。
 */
public record CreateCategoryL1Request(
        @JsonProperty("name")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String name) {
}
