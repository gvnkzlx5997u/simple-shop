package com.simpleshop.goods.enums;

/**
 * 商品状态。只有两态：在售与已下架；下架的具体原因记在 {@link OffShelfReason}。
 */
public enum GoodsStatus {
    ON_SALE,
    OFF_SHELF
}
