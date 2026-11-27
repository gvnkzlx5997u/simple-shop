package com.simpleshop.order.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.simpleshop.account.api.BuyerDirectoryPort;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.order.api.OrderAftersalePort;
import com.simpleshop.order.api.OrderAftersaleSummary;
import com.simpleshop.payment.api.PaymentPort;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderItemRepository;

/**
 * 订单查询的公共部分：分页与状态参数解析、买家用户名批量填充、订单件数统计与明细装配。
 *
 * <p>买卖两端查询范围不同，但返回结构一致，装配逻辑放在这里，避免两边各写一套。
 */
@Component
public class OrderQuerySupport {

    private final BuyerDirectoryPort buyers;
    private final OrderItemRepository orderItems;
    private final OrderAssembler assembler;
    private final AppProperties properties;
    private final PaymentPort payments;
    private final OrderAftersalePort aftersales;

    public OrderQuerySupport(
            BuyerDirectoryPort buyers,
            OrderItemRepository orderItems,
            OrderAssembler assembler,
            AppProperties properties,
            PaymentPort payments,
            OrderAftersalePort aftersales) {
        this.buyers = buyers;
        this.orderItems = orderItems;
        this.assembler = assembler;
        this.properties = properties;
        this.payments = payments;
        this.aftersales = aftersales;
    }

    public PageQuery pageQuery(Integer page, Integer pageSize) {
        return PageQuery.of(page, pageSize, properties);
    }

    /** 状态筛选只接受枚举里的七个值；空值表示不筛选，非法值按参数错误拒绝。 */
    public OrderStatus status(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    /** 一次批量补齐买家用户名与件数，避免列表页逐单查账号、逐单 count。 */
    public PageResponse<OrderData> pageResponse(Page<Order> result, PageQuery pageQuery) {
        List<Order> orders = result.getContent();
        Map<String, String> usernames = orders.isEmpty()
                ? Map.of()
                : buyers.usernames(orders.stream().map(Order::getBuyerId).toList());
        Map<String, Long> itemCounts = itemCounts(orders);
        List<OrderData> items = orders.stream()
                .map(order -> assembler.data(
                        order,
                        usernames.get(order.getBuyerId()),
                        itemCounts.getOrDefault(order.getId(), 0L),
                        refundedAmountOf(order),
                        aftersaleSummaryOf(order).count()))
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    public OrderData data(Order order) {
        String username = buyers.usernames(List.of(order.getBuyerId())).get(order.getBuyerId());
        long itemCount = orderItems.countByOrder_Id(order.getId());
        return assembler.data(
                order, username, itemCount,
                refundedAmountOf(order), aftersaleSummaryOf(order).count());
    }

    /**
     * 已退款 = 取消退款（支付包流水）+ 售后退款（售后单）。两处分开记，展示时相加，
     * 不在任何一张表里存「总退款」这个派生的数。
     */
    private BigDecimal refundedAmountOf(Order order) {
        return payments.refundedTotal(order.getId())
                .add(aftersaleSummaryOf(order).refundedAmount());
    }


    private OrderAftersaleSummary aftersaleSummaryOf(Order order) {
        return aftersales.summaryOf(order.getId());
    }

    public List<OrderItemData> items(String orderId) {
        return orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(orderId).stream()
                .map(assembler::item)
                .toList();
    }

    private Map<String, Long> itemCounts(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<String> orderIds = orders.stream().map(Order::getId).toList();
        Map<String, Long> counts = new HashMap<>(orderIds.size());
        for (OrderItemRepository.ItemCount row : orderItems.countByOrderIds(orderIds)) {
            counts.put(row.getOrderId(), row.getItemCount());
        }
        return counts;
    }
}
