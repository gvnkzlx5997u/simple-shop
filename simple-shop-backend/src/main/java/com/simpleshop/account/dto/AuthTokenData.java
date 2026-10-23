package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 登录成功后的令牌。
 */
public record AuthTokenData(
        @JsonProperty("token") String token,
        @JsonProperty("expire_at") String expireAt) {
}
