package com.simpleshop.common.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 分页响应体。页码从 1 开始，{@code page_size} 默认 10、上限 100。
 */
public record PageResponse<T>(
        @JsonProperty("items") List<T> items,
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total") long total) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        return new PageResponse<>(items, page, pageSize, total);
    }
}
