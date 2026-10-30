package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增二级类别。
 */
public record CreateCategoryL2Request(
        @JsonProperty("l1_id")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String l1Id,

        @JsonProperty("name")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String name) {
}
