package com.simpleshop.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个字段的校验失败说明。
 *
 * <p>{@code field} 使用请求体的 JSON 字段名（snake_case），嵌套字段用点号分隔，
 * 例如 {@code price}、{@code description[0].type}。
 */
public record FieldErrorData(
        @JsonProperty("field") String field,
        @JsonProperty("message") String message) {
}
