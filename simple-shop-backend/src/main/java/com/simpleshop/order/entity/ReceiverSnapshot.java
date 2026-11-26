package com.simpleshop.order.entity;

import com.simpleshop.common.text.TextRules;

/**
 * 下单时写进订单的收货信息快照。
 *
 * <p>只做「能不能落库」的校验：非空、长度、控制字符。手机号与地址的业务格式在地址簿
 * 创建时已经校验过；这里再校验一遍格式意义不大，但必须挡住超长与换行——
 * 地址列是一行文本，换行会把订单详情展示撑坏。
 */
public record ReceiverSnapshot(String name, String phone, String address) {

    private static final int NAME_MAX = 50;
    private static final int PHONE_MAX = 20;
    private static final int ADDRESS_MAX = 200;

    public ReceiverSnapshot {
        name = require(name, NAME_MAX, "receiver name");
        phone = require(phone, PHONE_MAX, "receiver phone");
        address = require(address, ADDRESS_MAX, "receiver address");
    }

    private static String require(String raw, int maxLength, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String value = raw.trim();
        if (TextRules.charCount(value) > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
        if (TextRules.containsControlChar(value)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return value;
    }
}
