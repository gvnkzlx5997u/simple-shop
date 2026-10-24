package com.simpleshop.history.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史描述块快照：形状与归档时的描述内容块一致，TEXT 有 text，IMAGE/VIDEO 有 url。
 */
public record HistoryDescriptionBlockData(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text,
        @JsonProperty("url") String url) {
}
