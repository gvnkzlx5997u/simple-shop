package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 客户详情：基本信息。电话完整展示不脱敏；不提供统计字段。
 *
 * <p>购买历史暂不返回：意向与归档机制已下线，改由订单提供，等订单模块可用后再补。
 */
public record CustomerDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("phone") String phone,
        @JsonProperty("default_location") String defaultLocation,
        @JsonProperty("create_at") String createAt) {
}
