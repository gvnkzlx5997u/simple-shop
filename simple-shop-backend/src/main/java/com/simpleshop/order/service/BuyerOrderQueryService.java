package com.simpleshop.order.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;

/**
 * 买家订单查询：只能看自己的订单，别人的订单按不存在处理。
 */
public interface BuyerOrderQueryService {

    /** 我的订单：全部状态，可按状态筛选，按下单时间倒序分页。 */
    PageResponse<OrderData> list(String buyerId, String status, Integer page, Integer pageSize);

    /** 订单详情：只回订单头，不含订单项。 */
    OrderData detail(String buyerId, String orderId);

    /** 订单项：按订单 ID 单独查，不分页。 */
    OrderItemsData items(String buyerId, String orderId);
}
