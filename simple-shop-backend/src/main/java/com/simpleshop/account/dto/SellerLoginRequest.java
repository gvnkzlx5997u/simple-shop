package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 卖家登录请求。
 */
public record SellerLoginRequest(
        @JsonProperty("account")
        @NotBlank(message = "must not be blank")
        String account,

        @JsonProperty("password")
        @NotBlank(message = "must not be blank")
        String password) {
}
