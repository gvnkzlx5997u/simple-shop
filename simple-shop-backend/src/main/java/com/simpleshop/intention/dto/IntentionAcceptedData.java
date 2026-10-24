package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 意向受理回执。不返回队列位次，买家看不到排队位置。
 */
public record IntentionAcceptedData(
        @JsonProperty("id") String id,
        @JsonProperty("create_at") String createAt) {
}
