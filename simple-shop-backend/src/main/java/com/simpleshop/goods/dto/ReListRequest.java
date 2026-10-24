package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 手动下架后的重新上架。必须显式确认，避免误触。
 */
public record ReListRequest(@JsonProperty("confirm") Boolean confirm) {
}
