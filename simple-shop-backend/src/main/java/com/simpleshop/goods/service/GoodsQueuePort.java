package com.simpleshop.goods.service;

/**
 * 商品队列的跨功能端口：发布时初始化序号，下架前检查是否存在非终态意向。
 * 由 intention 功能实现，避免 goods 反向依赖 intention。
 */
public interface GoodsQueuePort {

    void initialize(String goodsId);

    boolean hasActiveIntentions(String goodsId);
}
