package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卖家客户列表条目。字段仅限交易所需：用户名、电话与注册时间；
 * 不提供统计、搜索或导出。收货地址不在这里：它属于买家的地址簿与订单快照。
 */
public record CustomerSummaryData(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("phone") String phone,
        @JsonProperty("create_at") String createAt) {
}
