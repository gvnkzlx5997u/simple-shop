package com.simpleshop.history.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.history.api.ArchivePort;
import com.simpleshop.history.api.GoodsArchiveCommand;
import com.simpleshop.history.api.IntentionArchiveCommand;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.repository.GoodsHistoryRepository;
import com.simpleshop.history.repository.IntentionHistoryRepository;

/**
 * 成交售罄归档写入：商品与全部意向以快照形式迁入历史表。
 * 只接受已有事务（MANDATORY）：归档必须与成交同事务。
 */
@Service
public class ArchiveServiceImpl implements ArchivePort {

    private final GoodsHistoryRepository goodsHistories;
    private final IntentionHistoryRepository intentionHistories;

    public ArchiveServiceImpl(
            GoodsHistoryRepository goodsHistories,
            IntentionHistoryRepository intentionHistories) {
        this.goodsHistories = goodsHistories;
        this.intentionHistories = intentionHistories;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(GoodsArchiveCommand goods, List<IntentionArchiveCommand> intentions) {
        GoodsHistory goodsHistory = goodsHistories.saveAndFlush(GoodsHistory.archive(
                goods.id(),
                goods.name(),
                goods.price(),
                goods.finalStock(),
                goods.categoryL1Name(),
                goods.categoryL2Name(),
                goods.descriptionSnapshot(),
                goods.imagesSnapshot(),
                goods.createAt()));
        for (IntentionArchiveCommand input : intentions) {
            intentionHistories.save(IntentionHistory.archive(
                    input.id(),
                    goodsHistory,
                    input.goodsName(),
                    input.buyerId(),
                    input.buyerUsername(),
                    input.buyerPhone(),
                    input.createAt(),
                    input.result(),
                    input.failType(),
                    input.finishAt()));
        }
        intentionHistories.flush();
    }
}
