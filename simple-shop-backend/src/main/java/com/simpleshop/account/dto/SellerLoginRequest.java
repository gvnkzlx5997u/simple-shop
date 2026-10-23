package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卖家登录请求。
 */
public record SellerLoginRequest(
        @JsonProperty("account") String account,
        @JsonProperty("password") String password) {
}
