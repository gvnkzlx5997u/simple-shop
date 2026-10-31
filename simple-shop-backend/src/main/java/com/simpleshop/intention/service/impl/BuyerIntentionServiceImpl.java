package com.simpleshop.intention.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.service.BuyerAuthService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.security.RateLimiter;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.goods.api.GoodsLifecyclePort;
import com.simpleshop.intention.dto.IntentionAcceptedData;
import com.simpleshop.intention.dto.SubmitIntentionRequest;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;
import com.simpleshop.intention.service.BuyerIntentionService;

/**
 * 买家提交购买意向。提交不扣库存、不去重，同一买家可对同一商品重复提交。
 * 商品共享锁 → 队列序号行锁 → 意向行。
 */
@Service
public class BuyerIntentionServiceImpl implements BuyerIntentionService {

    private final GoodsLifecyclePort goodsLifecycle;
    private final BuyerAuthService accounts;
    private final GoodsQueueSequenceRepository sequences;
    private final IntentionRepository intentions;
    private final AppProperties properties;
    private final RateLimiter rateLimiter;
    private final OperationLogService audit;

    public BuyerIntentionServiceImpl(
            GoodsLifecyclePort goodsLifecycle,
            BuyerAuthService accounts,
            GoodsQueueSequenceRepository sequences,
            IntentionRepository intentions,
            AppProperties properties,
            RateLimiter rateLimiter,
            OperationLogService audit) {
        this.goodsLifecycle = goodsLifecycle;
        this.accounts = accounts;
        this.sequences = sequences;
        this.intentions = intentions;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional
    public IntentionAcceptedData submit(
            SubmitIntentionRequest request, AuthPrincipal buyerPrincipal, String clientKey) {
        if (!rateLimiter.tryAcquire(RateLimiter.Scope.SUBMIT_INTENTION, clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        String goodsId = request.goodsId() == null ? "" : request.goodsId().trim();
        goodsLifecycle.requireOnSaleForNewIntention(goodsId);
        if (intentions.countByGoodsIdAndStatusIn(
                goodsId, List.of(IntentionStatus.QUEUED, IntentionStatus.TRADING))
                >= properties.getQueue().getMaxSize()) {
            throw new BusinessException(ErrorCode.QUEUE_FULL);
        }
        sequences.createIfAbsent(goodsId);
        GoodsQueueSequence sequence = sequences.findWithLockByGoodsId(goodsId)
                .orElseThrow(() -> new IllegalStateException(
                        "queue sequence row missing for goods " + goodsId));
        Intention intention = intentions.saveAndFlush(Intention.submit(
                goodsId, accounts.requireAccount(buyerPrincipal.subjectId()), sequence.nextValue()));
        audit.log(OperationLogService.SUBMIT_INTENTION,
                OperationLogService.TARGET_INTENTION, intention.getId());
        return new IntentionAcceptedData(
                intention.getId(), UtcIso8601.of(intention.getCreateAt()));
    }
}
