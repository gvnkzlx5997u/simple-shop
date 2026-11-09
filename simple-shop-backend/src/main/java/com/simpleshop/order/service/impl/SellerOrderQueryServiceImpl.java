package com.simpleshop.order.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.OrderQuerySupport;
import com.simpleshop.order.service.SellerOrderQueryService;

/**
 * 卖家订单查询：订单管理、按客户、按商品共用一条列表查询，只是筛选条件不同。
 */
@Service
public class SellerOrderQueryServiceImpl implements SellerOrderQueryService {

    private final OrderRepository orders;
    private final OrderQuerySupport support;

    public SellerOrderQueryServiceImpl(OrderRepository orders, OrderQuerySupport support) {
        this.orders = orders;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderData> list(
            String status, String buyerId, String goodsId, Integer page, Integer pageSize) {
        PageQuery pageQuery = support.pageQuery(page, pageSize);
        Page<Order> result = orders.search(
                support.status(status),
                TextRules.trimToNull(buyerId),
                TextRules.trimToNull(goodsId),
                pageQuery.pageable());
        return support.pageResponse(result, pageQuery);
    }

    @Transactional(readOnly = true)
    public OrderData detail(String orderId) {
        return support.data(require(orderId));
    }

    @Transactional(readOnly = true)
    public OrderItemsData items(String orderId) {
        Order order = require(orderId);
        return new OrderItemsData(support.items(order.getId()));
    }

    private Order require(String orderId) {
        String id = orderId == null ? "" : orderId.trim();
        return orders.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
