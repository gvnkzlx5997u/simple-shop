package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卖家意向名单条目。买家联系方式实时读账号，归档时才固化为快照。
 */
public record IntentionEntryData(
        @JsonProperty("id") String id,
        @JsonProperty("queue_order") long queueOrder,
        @JsonProperty("status") String status,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("buyer_username") String buyerUsername,
        @JsonProperty("buyer_phone") String buyerPhone,
        @JsonProperty("buyer_default_location") String buyerDefaultLocation,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("finish_at") String finishAt) {
}
