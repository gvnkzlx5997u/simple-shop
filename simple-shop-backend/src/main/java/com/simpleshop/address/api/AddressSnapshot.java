package com.simpleshop.address.api;

/**
 * 地址快照：跨功能只读契约，不携带实体。
 *
 * <p>下单只需要这三项，拿到后立刻写进订单，之后地址簿怎么改都与订单无关。
 */
public record AddressSnapshot(
        String id,
        String receiverName,
        String receiverPhone,
        String detailAddress) {
}
