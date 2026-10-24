package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卖家客户列表条目。字段仅限交易所需：用户名、电话、默认交易地点与注册时间；
 * 不提供统计、搜索或导出。
 */
public record CustomerSummaryData(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("phone") String phone,
        @JsonProperty("default_location") String defaultLocation,
        @JsonProperty("create_at") String createAt) {
}
