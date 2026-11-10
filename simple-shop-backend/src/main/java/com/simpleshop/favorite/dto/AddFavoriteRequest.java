package com.simpleshop.favorite.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddFavoriteRequest(
        @JsonProperty("goods_id") @NotBlank String goodsId) {
}
