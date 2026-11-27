package com.simpleshop.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建或编辑地址。只有三个字段，不做省市区与邮编。
 *
 * <p>边界只挡「空与超长」；电话格式、换行、尖括号这些业务规则由 {@code AddressRules} 判定，
 * 失败返回 80003。
 */
public record AddressRequest(
        @JsonProperty("receiver_name")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String receiverName,

        @JsonProperty("receiver_phone")
        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String receiverPhone,

        @JsonProperty("detail_address")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 200)
        String detailAddress) {
}
