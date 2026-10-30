package com.simpleshop.intention.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.goods.api.GoodsArchiveSnapshot;
import com.simpleshop.goods.api.GoodsLifecyclePort;
import com.simpleshop.goods.api.TradingStart;
import com.simpleshop.history.api.ArchivePort;
import com.simpleshop.history.api.GoodsArchiveCommand;
import com.simpleshop.history.api.IntentionArchiveCommand;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.dto.TradeFailureRequest;
import com.simpleshop.intention.dto.TradeResultData;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.enums.TradeDisposal;
import com.simpleshop.intention.enums.TradeGoodsStatus;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;
import com.simpleshop.intention.service.IntentionEntryAssembler;
import com.simpleshop.intention.service.SellerTradeService;
import com.simpleshop.inventory.api.StockPort;

/**
 * 卖家交易裁决：进入交易、标记成功、标记失败。
 * 商品状态只经 GoodsLifecyclePort 变更；锁顺序：商品行 → 队列序号行 → 意向行 → 库存行。
 */
@Service
public class SellerTradeServiceImpl implements SellerTradeService {

    private final GoodsLifecyclePort goodsLifecycle;
    private final IntentionRepository intentions;
    private final GoodsQueueSequenceRepository sequences;
    private final ArchivePort archivePort;
    private final StockPort stockPort;
    private final OperationLogService audit;

    public SellerTradeServiceImpl(
            GoodsLifecyclePort goodsLifecycle,
            IntentionRepository intentions,
            GoodsQueueSequenceRepository sequences,
            ArchivePort archivePort,
            StockPort stockPort,
            OperationLogService audit) {
        this.goodsLifecycle = goodsLifecycle;
        this.intentions = intentions;
        this.sequences = sequences;
        this.archivePort = archivePort;
        this.stockPort = stockPort;
        this.audit = audit;
    }

    /** 进入交易：商品在售、该意向为队首；同一商品同时至多一笔交易。 */
    @Transactional
    public IntentionEntryData enterTrade(String intentionId) {
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoodsId();

        TradingStart start = goodsLifecycle.lockForTrading(goodsId);
        switch (start) {
            case ALREADY_TRADING -> throw new BusinessException(ErrorCode.TRADING_ALREADY_EXISTS);
            case NOT_ON_SALE -> throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
            case STARTED -> {
            }
        }
        if (intentions.countByGoodsIdAndStatusIn(goodsId, List.of(IntentionStatus.TRADING)) > 0) {
            throw new BusinessException(ErrorCode.TRADING_ALREADY_EXISTS);
        }

        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.QUEUED) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }
        String headId = intentions
                .findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.QUEUED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getId();
        if (!headId.equals(id)) {
            throw new BusinessException(ErrorCode.NOT_QUEUE_HEAD);
        }

        goodsLifecycle.startTrading(goodsId);
        intention.enterTrade();
        intentions.saveAndFlush(intention);
        audit.log(OperationLogService.ENTER_TRADE,
                OperationLogService.TARGET_INTENTION, intention.getId());
        return IntentionEntryAssembler.entry(intention);
    }

    /** 标记交易成功：扣库存后大于 0 商品回在售；等于 0 商品与全部意向同事务归档。 */
    @Transactional
    public TradeResultData markSuccess(String intentionId) {
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoodsId();

        goodsLifecycle.requireTrading(goodsId);
        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.TRADING) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }

        int remaining = stockPort.deductOne(goodsId);
        if (remaining > 0) {
            intention.markSucceeded();
            goodsLifecycle.backToOnSale(goodsId);
            intentions.saveAndFlush(intention);
            audit.log(OperationLogService.MARK_TRADE_SUCCESS,
                    OperationLogService.TARGET_INTENTION, intention.getId());
            return new TradeResultData(
                    IntentionEntryAssembler.entry(intention), TradeGoodsStatus.ON_SALE);
        }
        return archiveSoldOut(goodsId, intention, remaining);
    }

    /**
     * 成交售罄归档，顺序固定：其余排队中置失败 → 本次意向置成功 → 写历史快照 →
     * 删全部当前意向行 → 删商品行。全程当前事务，禁止拆分。
     */
    private TradeResultData archiveSoldOut(String goodsId, Intention intention, int finalStock) {
        List<Intention> queued = intentions.findByGoodsIdAndStatusOrderByQueueOrderAsc(
                goodsId, IntentionStatus.QUEUED);
        for (Intention other : queued) {
            other.markFailed(FailType.SOLD_OUT);
        }
        intention.markSucceeded();
        intentions.saveAllAndFlush(queued);
        intentions.saveAndFlush(intention);

        GoodsArchiveSnapshot snapshot = goodsLifecycle.snapshotForArchive(goodsId, finalStock);
        List<IntentionArchiveCommand> inputs = intentions
                .findByGoodsIdOrderByQueueOrderAsc(goodsId).stream()
                .map(value -> archiveInput(value, snapshot.name()))
                .toList();
        archivePort.archive(
                new GoodsArchiveCommand(
                        snapshot.id(),
                        snapshot.name(),
                        snapshot.price(),
                        snapshot.finalStock(),
                        snapshot.categoryL1Name(),
                        snapshot.categoryL2Name(),
                        snapshot.descriptionSnapshot(),
                        snapshot.imagesSnapshot(),
                        snapshot.createAt()),
                inputs);

        intentions.deleteRowByGoodsId(goodsId);
        goodsLifecycle.removeArchived(goodsId);
        audit.log(OperationLogService.MARK_TRADE_SUCCESS,
                OperationLogService.TARGET_INTENTION, intention.getId());
        return new TradeResultData(
                IntentionEntryAssembler.entry(intention), TradeGoodsStatus.ARCHIVED);
    }

    private static IntentionArchiveCommand archiveInput(Intention value, String goodsName) {
        IntentionResult result = value.getStatus() == IntentionStatus.SUCCEEDED
                ? IntentionResult.SUCCEEDED
                : IntentionResult.FAILED;
        return new IntentionArchiveCommand(
                value.getId(),
                goodsName,
                value.getBuyer().getId(),
                value.getBuyer().getUsername(),
                value.getBuyer().getPhone(),
                value.getCreateAt(),
                result,
                historyFailType(value.getFailType()),
                value.getFinishAt());
    }

    private static com.simpleshop.history.enums.FailType historyFailType(FailType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SOLD_OUT -> com.simpleshop.history.enums.FailType.SOLD_OUT;
            case SELLER_VOIDED -> com.simpleshop.history.enums.FailType.SELLER_VOIDED;
        };
    }

    /**
     * 标记交易失败并裁决。作废：意向置失败（卖家作废）；重新排队：序号刷新至队尾、
     * 下单时间不变。商品回在售，库存不变。重排按锁序先取序号行再锁意向行。
     */
    @Transactional
    public TradeResultData markFailure(String intentionId, TradeFailureRequest request) {
        TradeDisposal disposal = disposal(request);
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoodsId();

        goodsLifecycle.requireTrading(goodsId);
        GoodsQueueSequence sequence = null;
        if (disposal == TradeDisposal.REQUEUED) {
            sequences.createIfAbsent(goodsId);
            sequence = sequences.findWithLockByGoodsId(goodsId)
                    .orElseThrow(() -> new IllegalStateException(
                            "queue sequence row missing for goods " + goodsId));
        }
        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.TRADING) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }

        if (disposal == TradeDisposal.REQUEUED) {
            intention.requeue(sequence.nextValue());
        } else {
            intention.markFailed(FailType.SELLER_VOIDED);
        }
        goodsLifecycle.backToOnSale(goodsId);
        intentions.saveAndFlush(intention);
        audit.log(OperationLogService.MARK_TRADE_FAILURE,
                OperationLogService.TARGET_INTENTION, intention.getId(), disposal.name());
        return new TradeResultData(
                IntentionEntryAssembler.entry(intention), TradeGoodsStatus.ON_SALE);
    }

    private static TradeDisposal disposal(TradeFailureRequest request) {
        TradeDisposal value = request == null ? null : request.disposal();
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_DISPOSAL);
        }
        return value;
    }

    private static String requireIntentionId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.INTENTION_NOT_FOUND);
        }
        return value;
    }
}
