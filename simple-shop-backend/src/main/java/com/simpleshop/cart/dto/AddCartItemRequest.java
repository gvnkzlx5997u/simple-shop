package com.simpleshop.cart.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddCartItemRequest(
        @JsonProperty("goods_id") @NotBlank String goodsId) {
}
