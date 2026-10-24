package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.common.api.PageResponse;

/**
 * 客户详情：基本信息加购买历史分页。电话完整展示不脱敏；不提供统计字段。
 */
public record CustomerDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("phone") String phone,
        @JsonProperty("default_location") String defaultLocation,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("purchase_history") PageResponse<CustomerPurchaseEntryData> purchaseHistory) {
}
