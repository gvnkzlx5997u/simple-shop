package com.simpleshop.history.api;

import java.time.LocalDateTime;

import com.simpleshop.history.enums.FailType;
import com.simpleshop.history.enums.IntentionResult;

/**
 * 归档意向快照：买家与商品信息均为字符串快照。
 */
public record IntentionArchiveCommand(
        String id,
        String goodsName,
        String buyerId,
        String buyerUsername,
        String buyerPhone,
        LocalDateTime createAt,
        IntentionResult result,
        FailType failType,
        LocalDateTime finishAt) {
}
