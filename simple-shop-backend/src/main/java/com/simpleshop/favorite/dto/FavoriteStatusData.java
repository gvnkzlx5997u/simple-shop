package com.simpleshop.favorite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 商品详情页的收藏状态：{@code favorited} 为 true 表示当前买家收藏过这件商品。 */
public record FavoriteStatusData(
        @JsonProperty("favorited") boolean favorited) {
}
