package com.simpleshop.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 类别重命名。
 */
public record RenameCategoryRequest(@JsonProperty("name") String name) {
}
