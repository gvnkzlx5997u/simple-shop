package com.simpleshop.order.service.impl;

import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.BuyerOrderQueryService;
import com.simpleshop.order.service.OrderQuerySupport;

/**
 * 买家订单查询。归属校验放在最前面：订单不属于当前买家时一律按「订单不存在」返回，
 * 不泄露他人订单是否存在。
 */
@Service
public class BuyerOrderQueryServiceImpl implements BuyerOrderQueryService {

    private final OrderRepository orders;
    private final OrderQuerySupport support;

    public BuyerOrderQueryServiceImpl(OrderRepository orders, OrderQuerySupport support) {
        this.orders = orders;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderData> list(
            String buyerId, String status, Integer page, Integer pageSize) {
        PageQuery pageQuery = support.pageQuery(page, pageSize);
        Page<Order> result =
                orders.search(support.status(status), buyerId, null, pageQuery.pageable());
        return support.pageResponse(result, pageQuery);
    }

    @Transactional(readOnly = true)
    public OrderData detail(String buyerId, String orderId) {
        return support.data(requireOwned(buyerId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderItemsData items(String buyerId, String orderId) {
        Order order = requireOwned(buyerId, orderId);
        return new OrderItemsData(support.items(order.getId()));
    }

    private Order requireOwned(String buyerId, String orderId) {
        String id = orderId == null ? "" : orderId.trim();
        Order order = orders.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }
}
