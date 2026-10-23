package com.simpleshop.intention.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.simpleshop.goods.service.GoodsQueuePort;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 商品队列端口实现：创建序号行，并按商品统计排队中/交易中意向。
 */
@Component
public class GoodsQueueAdapter implements GoodsQueuePort {

    private final GoodsQueueSequenceRepository sequences;
    private final IntentionRepository intentions;

    public GoodsQueueAdapter(
            GoodsQueueSequenceRepository sequences, IntentionRepository intentions) {
        this.sequences = sequences;
        this.intentions = intentions;
    }

    @Override
    public void initialize(String goodsId) {
        sequences.save(GoodsQueueSequence.forGoods(goodsId));
    }

    @Override
    public boolean hasActiveIntentions(String goodsId) {
        return intentions.countByGoods_IdAndStatusIn(
                goodsId, List.of(IntentionStatus.QUEUED, IntentionStatus.TRADING)) > 0;
    }
}
