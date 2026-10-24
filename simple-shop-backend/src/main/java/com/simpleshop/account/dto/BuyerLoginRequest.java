package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 买家登录请求。
 */
public record BuyerLoginRequest(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password) {
}
