package com.simpleshop.aftersale.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.simpleshop.aftersale.dto.AftersaleAppliedData;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.aftersale.entity.Aftersale;
import com.simpleshop.aftersale.entity.AftersaleImage;
import com.simpleshop.aftersale.repository.AftersaleImageRepository;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.order.api.AftersaleTarget;
import com.simpleshop.order.api.OrderTradePort;

/**
 * 售后单到响应 DTO 的映射，买卖两端共用。
 *
 * <p>{@code order_no}、{@code goods_name} 从订单端口现取，售后单本身不存这两个字段——
 * 存了就会和订单里的快照各说各话。卖家列表按页取，每行一次端口调用，页大小 ≤ 20 可以接受。
 */
@Component
public class AftersaleAssembler {

    private final OrderTradePort orders;
    private final AftersaleImageRepository images;

    public AftersaleAssembler(OrderTradePort orders, AftersaleImageRepository images) {
        this.orders = orders;
        this.images = images;
    }

    public AftersaleAppliedData applied(Aftersale aftersale) {
        return new AftersaleAppliedData(
                aftersale.getId(),
                aftersale.getAftersaleNo(),
                aftersale.getStatus(),
                UtcIso8601.of(aftersale.getCreateAt()));
    }

    public AftersaleData summary(Aftersale aftersale) {
        AftersaleTarget target = orders
                .findAftersaleTarget(aftersale.getBuyerId(), aftersale.getOrderItemId())
                .orElse(null);
        return new AftersaleData(
                aftersale.getId(),
                aftersale.getAftersaleNo(),
                aftersale.getOrderId(),
                target == null ? null : target.orderNo(),
                aftersale.getOrderItemId(),
                target == null ? null : target.goodsName(),
                aftersale.getTitle(),
                aftersale.getStatus(),
                aftersale.getResult(),
                aftersale.getRefundAmount(),
                isoOrNull(aftersale.getRefundedAt()),
                UtcIso8601.of(aftersale.getCreateAt()));
    }

    public AftersaleDetailData detail(Aftersale aftersale) {
        AftersaleTarget target = orders
                .findAftersaleTarget(aftersale.getBuyerId(), aftersale.getOrderItemId())
                .orElse(null);
        List<String> urls = images.findByAftersale_IdOrderBySortNoAsc(aftersale.getId())
                .stream()
                .map(AftersaleImage::getUrl)
                .toList();
        return new AftersaleDetailData(
                aftersale.getId(),
                aftersale.getAftersaleNo(),
                aftersale.getOrderId(),
                target == null ? null : target.orderNo(),
                aftersale.getOrderItemId(),
                target == null ? null : target.goodsName(),
                aftersale.getTitle(),
                aftersale.getDescription(),
                urls,
                aftersale.getStatus(),
                aftersale.getResult(),
                aftersale.getRefundAmount(),
                isoOrNull(aftersale.getRefundedAt()),
                UtcIso8601.of(aftersale.getCreateAt()),
                UtcIso8601.of(aftersale.getUpdateAt()));
    }

    private static String isoOrNull(java.time.LocalDateTime value) {
        return value == null ? null : UtcIso8601.of(value);
    }
}
