package com.simpleshop.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 转收藏结果：{@code moved_count} 是真正转过去的件数，等于从购物车删除的行数；
 * 重复转同一批时为 0。入参里不在购物车的商品不计入，也不会被顺手写进收藏。
 */
public record MoveToFavoritesData(
        @JsonProperty("moved_count") int movedCount) {
}
