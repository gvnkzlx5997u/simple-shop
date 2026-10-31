package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 买家注册请求。存在性与长度在边界校验；字符规则与重复性仍由账号规则判定。
 */
public record BuyerRegisterRequest(
        @JsonProperty("username")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String username,

        @JsonProperty("password")
        @NotBlank(message = "must not be blank")
        @CodePointLength(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @JsonProperty("phone")
        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String phone,

        @JsonProperty("default_location")
        @CodePointLength(max = 50)
        String defaultLocation) {
}
