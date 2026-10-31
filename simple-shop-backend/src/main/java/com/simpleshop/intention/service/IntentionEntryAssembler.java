package com.simpleshop.intention.service;

import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.entity.Intention;

/**
 * 意向名单条目映射：查询与交易裁决共用，避免写事务调用查询服务。
 */
public final class IntentionEntryAssembler {

    private IntentionEntryAssembler() {
    }

    public static IntentionEntryData entry(Intention intention) {
        return new IntentionEntryData(
                intention.getId(),
                intention.getQueueOrder(),
                intention.getStatus(),
                intention.getFailType(),
                intention.getBuyer().getUsername(),
                intention.getBuyer().getPhone(),
                intention.getBuyer().getDefaultLocation(),
                UtcIso8601.of(intention.getCreateAt()),
                UtcIso8601.of(intention.getFinishAt()));
    }
}
