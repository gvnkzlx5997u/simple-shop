package com.simpleshop.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 卖家修改密码请求。
 */
public record ChangePasswordRequest(
        @JsonProperty("old_password")
        @NotBlank(message = "must not be blank")
        String oldPassword,

        @JsonProperty("new_password")
        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String newPassword) {
}
