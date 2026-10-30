package com.simpleshop.intention.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.simpleshop.goods.api.QueueActivityPort;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 队列活跃度端口实现：按商品统计排队中/交易中意向。
 */
@Component
public class QueueActivityAdapter implements QueueActivityPort {

    private final IntentionRepository intentions;

    public QueueActivityAdapter(IntentionRepository intentions) {
        this.intentions = intentions;
    }

    @Override
    public boolean hasActiveIntentions(String goodsId) {
        return intentions.countByGoodsIdAndStatusIn(
                goodsId, List.of(IntentionStatus.QUEUED, IntentionStatus.TRADING)) > 0;
    }
}
