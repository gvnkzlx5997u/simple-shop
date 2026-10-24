package com.simpleshop.intention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 交易失败裁决请求。disposal 取 VOIDED（作废）或 REQUEUED（重新排队到队尾）。
 */
public record TradeFailureRequest(
        @JsonProperty("disposal") String disposal) {
}
