package com.simpleshop.history.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史意向条目（卖家视角）：买家用户名与电话为归档快照，电话完整展示不脱敏。
 * 历史表不保存队列序号。
 */
public record IntentionHistoryEntryData(
        @JsonProperty("id") String id,
        @JsonProperty("buyer_username") String buyerUsername,
        @JsonProperty("buyer_phone") String buyerPhone,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("result") String result,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("finish_at") String finishAt) {
}
