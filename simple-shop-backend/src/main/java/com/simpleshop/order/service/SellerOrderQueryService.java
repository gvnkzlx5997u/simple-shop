package com.simpleshop.order.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;

/**
 * 卖家订单查询：订单管理、按客户、按商品三个入口只是筛选条件不同，
 * 详情与明细一律按订单主键查。
 */
public interface SellerOrderQueryService {

    /**
     * 订单列表：状态、客户（买家 ID）、商品三个筛选条件可任意组合，全部为空时返回全部订单。
     * 按商品筛选经订单项反查订单 ID。
     */
    PageResponse<OrderData> list(
            String status, String buyerId, String goodsId, Integer page, Integer pageSize);

    /** 订单详情：只回订单头，不含订单项。 */
    OrderData detail(String orderId);

    /** 订单项：按订单 ID 单独查，不分页。 */
    OrderItemsData items(String orderId);
}
