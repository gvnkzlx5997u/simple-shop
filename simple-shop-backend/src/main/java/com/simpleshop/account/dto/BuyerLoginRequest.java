package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 买家登录请求。
 */
public record BuyerLoginRequest(
        @JsonProperty("username")
        @NotBlank(message = "must not be blank")
        String username,

        @JsonProperty("password")
        @NotBlank(message = "must not be blank")
        String password) {
}
