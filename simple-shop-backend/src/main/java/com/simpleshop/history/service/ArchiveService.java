package com.simpleshop.history.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.history.repository.GoodsHistoryRepository;
import com.simpleshop.history.repository.IntentionHistoryRepository;
import com.simpleshop.intention.enums.FailType;

/**
 * 成交售罄的归档写入：商品与全部意向以快照形式迁入历史表。
 * 入参是纯快照数据，不接收 goods/intention 的当前实体；历史表不引用当前表。
 * 只接受已有事务（MANDATORY）：归档必须与成交同事务，禁止异步或事后归档。
 */
@Service
public class ArchiveService {

    private final GoodsHistoryRepository goodsHistories;
    private final IntentionHistoryRepository intentionHistories;

    public ArchiveService(
            GoodsHistoryRepository goodsHistories,
            IntentionHistoryRepository intentionHistories) {
        this.goodsHistories = goodsHistories;
        this.intentionHistories = intentionHistories;
    }

    /** 前置条件：全部意向均已置终态（finishAt 非空），由数据库 CHECK 约束兜底。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(GoodsArchiveInput goods, List<IntentionArchiveInput> intentions) {
        GoodsHistory goodsHistory = goodsHistories.saveAndFlush(GoodsHistory.archive(
                goods.id(),
                goods.name(),
                goods.price(),
                goods.stock(),
                goods.categoryL1Name(),
                goods.categoryL2Name(),
                goods.descriptionSnapshot(),
                goods.imagesSnapshot(),
                goods.createAt()));
        for (IntentionArchiveInput input : intentions) {
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

    /** 商品归档快照；descriptionSnapshot / imagesSnapshot 为 JSON 字符串。 */
    public record GoodsArchiveInput(
            String id,
            String name,
            BigDecimal price,
            int stock,
            String categoryL1Name,
            String categoryL2Name,
            String descriptionSnapshot,
            String imagesSnapshot,
            LocalDateTime createAt) {
    }

    /** 意向归档快照；买家用户名、电话与商品名为归档时的字符串快照。 */
    public record IntentionArchiveInput(
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
}
