package com.simpleshop.history.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史商品详情：描述与图片为 JSON 快照的解析结果，意向名单为该商品全部历史意向。
 */
public record GoodsHistoryDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("category_l1_name") String categoryL1Name,
        @JsonProperty("category_l2_name") String categoryL2Name,
        @JsonProperty("result") String result,
        @JsonProperty("description") List<HistoryDescriptionBlockData> description,
        @JsonProperty("images") List<HistoryImageData> images,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("archived_at") String archivedAt,
        @JsonProperty("intentions") List<IntentionHistoryEntryData> intentions) {
}
