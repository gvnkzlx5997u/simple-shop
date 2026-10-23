package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 买家注册请求。字段校验在 Service 层完成。
 */
public record BuyerRegisterRequest(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password,
        @JsonProperty("phone") String phone,
        @JsonProperty("default_location") String defaultLocation) {
}
