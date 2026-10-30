package com.simpleshop.goods.api;

/**
 * 商品队列活跃度只读端口：由 intention 实现，供 goods 在下架前检查。
 */
public interface QueueActivityPort {

    boolean hasActiveIntentions(String goodsId);
}
