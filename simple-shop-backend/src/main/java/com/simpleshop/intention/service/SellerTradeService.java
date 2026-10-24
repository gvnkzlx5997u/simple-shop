package com.simpleshop.intention.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.goods.dto.GoodsArchiveSnapshot;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.service.GoodsTradeSupport;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.history.service.ArchiveService;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.dto.TradeFailureRequest;
import com.simpleshop.intention.dto.TradeResultData;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 卖家交易裁决：进入交易、标记成功、标记失败。
 * 前置条件在商品行锁内校验，锁顺序：商品行 → 意向行；
 * 商品状态与意向状态在同一个事务内变更。
 */
@Service
public class SellerTradeService {

    /** 归档裁决响应中的商品状态值；当前商品表没有 ARCHIVED 取值。 */
    private static final String GOODS_ARCHIVED = "ARCHIVED";

    private static final String DISPOSAL_VOIDED = "VOIDED";
    private static final String DISPOSAL_REQUEUED = "REQUEUED";

    private final GoodsTradeSupport goodsTradeSupport;
    private final IntentionRepository intentions;
    private final GoodsQueueSequenceRepository sequences;
    private final SellerIntentionQueryService intentionQuery;
    private final ArchiveService archiveService;
    private final OperationLogService audit;

    public SellerTradeService(
            GoodsTradeSupport goodsTradeSupport,
            IntentionRepository intentions,
            GoodsQueueSequenceRepository sequences,
            SellerIntentionQueryService intentionQuery,
            ArchiveService archiveService,
            OperationLogService audit) {
        this.goodsTradeSupport = goodsTradeSupport;
        this.intentions = intentions;
        this.sequences = sequences;
        this.intentionQuery = intentionQuery;
        this.archiveService = archiveService;
        this.audit = audit;
    }

    /** 进入交易：商品在售、无其他交易中意向、该意向为队首，三者缺一不可。 */
    @Transactional
    public IntentionEntryData enterTrade(String intentionId) {
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoods().getId();

        Goods goods = goodsTradeSupport.lockById(goodsId);
        if (goods.getStatus() == GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.TRADING_ALREADY_EXISTS);
        }
        if (!goods.isOnSale()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
        if (intentions.countByGoods_IdAndStatusIn(goodsId, List.of(IntentionStatus.TRADING)) > 0) {
            throw new BusinessException(ErrorCode.TRADING_ALREADY_EXISTS);
        }

        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.QUEUED) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }
        String headId = intentions
                .findFirstByGoods_IdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.QUEUED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getId();
        if (!headId.equals(id)) {
            throw new BusinessException(ErrorCode.NOT_QUEUE_HEAD);
        }

        intention.enterTrade();
        goods.changeStatus(GoodsStatus.FROZEN);
        intentions.saveAndFlush(intention);
        audit.log(OperationLogService.ENTER_TRADE,
                OperationLogService.TARGET_INTENTION, intention.getId());
        return intentionQuery.entryOf(intention);
    }

    /** 标记交易成功：扣库存后大于 0 商品回在售；等于 0 商品与全部意向同事务归档。 */
    @Transactional
    public TradeResultData markSuccess(String intentionId) {
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoods().getId();

        Goods goods = goodsTradeSupport.lockById(goodsId);
        if (goods.getStatus() != GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.TRADING) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }

        goods.changeStock(goods.getStock() - 1);
        if (goods.getStock() > 0) {
            intention.markSucceeded();
            goods.changeStatus(GoodsStatus.ON_SALE);
            intentions.saveAndFlush(intention);
            audit.log(OperationLogService.MARK_TRADE_SUCCESS,
                    OperationLogService.TARGET_INTENTION, intention.getId());
            return new TradeResultData(
                    intentionQuery.entryOf(intention), GoodsStatus.ON_SALE.name());
        }
        return archiveSoldOut(goods, intention);
    }

    /**
     * 成交售罄归档，顺序固定：其余排队中置失败 → 本次意向置成功 → 写历史快照 →
     * 删全部当前意向行 → 删商品行。全程当前事务，禁止拆分；
     * 两条删除都是批量语句，删除后不得再访问当前实体。
     */
    private TradeResultData archiveSoldOut(Goods goods, Intention intention) {
        String goodsId = goods.getId();
        List<Intention> queued = intentions.findByGoods_IdAndStatusOrderByQueueOrderAsc(
                goodsId, IntentionStatus.QUEUED);
        for (Intention other : queued) {
            other.markFailed(FailType.SOLD_OUT);
        }
        intention.markSucceeded();
        intentions.saveAllAndFlush(queued);
        intentions.saveAndFlush(intention);

        GoodsArchiveSnapshot snapshot = goodsTradeSupport.buildArchiveSnapshot(goods);
        List<ArchiveService.IntentionArchiveInput> inputs = intentions
                .findByGoods_IdOrderByQueueOrderAsc(goodsId).stream()
                .map(SellerTradeService::archiveInput)
                .toList();
        archiveService.archive(
                new ArchiveService.GoodsArchiveInput(
                        snapshot.id(),
                        snapshot.name(),
                        snapshot.price(),
                        snapshot.stock(),
                        snapshot.categoryL1Name(),
                        snapshot.categoryL2Name(),
                        snapshot.descriptionSnapshot(),
                        snapshot.imagesSnapshot(),
                        snapshot.createAt()),
                inputs);

        intentions.deleteRowByGoodsId(goodsId);
        goodsTradeSupport.removeArchived(goodsId);
        audit.log(OperationLogService.MARK_TRADE_SUCCESS,
                OperationLogService.TARGET_INTENTION, intention.getId());
        return new TradeResultData(intentionQuery.entryOf(intention), GOODS_ARCHIVED);
    }

    private static ArchiveService.IntentionArchiveInput archiveInput(Intention value) {
        IntentionResult result = value.getStatus() == IntentionStatus.SUCCEEDED
                ? IntentionResult.SUCCEEDED
                : IntentionResult.FAILED;
        return new ArchiveService.IntentionArchiveInput(
                value.getId(),
                value.getGoods().getName(),
                value.getBuyer().getId(),
                value.getBuyer().getUsername(),
                value.getBuyer().getPhone(),
                value.getCreateAt(),
                result,
                value.getFailType(),
                value.getFinishAt());
    }

    /**
     * 标记交易失败并裁决。作废：意向置失败（卖家作废）；重新排队：序号刷新至队尾、
     * 下单时间不变。商品回在售，库存不变。重排按锁序先取序号行再锁意向行。
     */
    @Transactional
    public TradeResultData markFailure(String intentionId, TradeFailureRequest request) {
        String disposal = disposal(request);
        String id = requireIntentionId(intentionId);
        String goodsId = intentions.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND))
                .getGoods().getId();

        Goods goods = goodsTradeSupport.lockById(goodsId);
        if (goods.getStatus() != GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        GoodsQueueSequence sequence = null;
        if (DISPOSAL_REQUEUED.equals(disposal)) {
            sequence = sequences.findWithLockByGoodsId(goodsId)
                    .orElseThrow(() -> new IllegalStateException(
                            "queue sequence row missing for goods " + goodsId));
        }
        Intention intention = intentions.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (intention.getStatus() != IntentionStatus.TRADING) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }

        if (DISPOSAL_REQUEUED.equals(disposal)) {
            intention.requeue(sequence.nextValue());
        } else {
            intention.markFailed(FailType.SELLER_VOIDED);
        }
        goods.changeStatus(GoodsStatus.ON_SALE);
        intentions.saveAndFlush(intention);
        audit.log(OperationLogService.MARK_TRADE_FAILURE,
                OperationLogService.TARGET_INTENTION, intention.getId(), disposal);
        return new TradeResultData(
                intentionQuery.entryOf(intention), GoodsStatus.ON_SALE.name());
    }

    private static String disposal(TradeFailureRequest request) {
        String value = request == null || request.disposal() == null
                ? ""
                : request.disposal().trim().toUpperCase(Locale.ROOT);
        if (!DISPOSAL_VOIDED.equals(value) && !DISPOSAL_REQUEUED.equals(value)) {
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
