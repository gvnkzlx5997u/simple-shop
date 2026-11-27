package com.simpleshop.aftersale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.aftersale.enums.AftersaleStatus;

/**
 * 提交成功的返回：只回单号与状态，避免与详情两套结构。
 */
public record AftersaleAppliedData(
        @JsonProperty("aftersale_id") String aftersaleId,
        @JsonProperty("aftersale_no") String aftersaleNo,
        @JsonProperty("status") AftersaleStatus status,
        @JsonProperty("create_at") String createAt) {
}
