package com.simpleshop.aftersale.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.aftersale.dto.AftersaleCompleteRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.aftersale.entity.Aftersale;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.aftersale.service.AftersaleAssembler;
import com.simpleshop.aftersale.service.SellerAftersaleService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.order.api.OrderItemSnapshot;
import com.simpleshop.order.api.OrderTradePort;

/**
 * 卖家售后实现。
 *
 * <p>「完成并退款」的三步顺序不能换：锁订单项 → 查该订单项是否已有退款 → 写金额。
 * 订单项是退款额度的串行化锚点：同一个订单项可能挂着多张售后单，只锁当前这张挡不住
 * 另一张同时退款。额度校验放在写金额之前，撞上了整笔回滚，不会留下半条已退款的单。
 *
 * <p>售后退款只落售后单，不写支付流水、不改库存、不上架——这是团队口径，
 * 顺手补一条流水会让订单详情的「已退款」重复计数。
 */
@Service
public class SellerAftersaleServiceImpl implements SellerAftersaleService {

    /** 产生退款的结果：占用该订单项唯一的退款额度。 */
    private static final List<AftersaleResult> REFUND_RESULTS =
            List.of(AftersaleResult.RETURN_AND_REFUND, AftersaleResult.REFUND_ONLY);

    private final AftersaleRepository aftersales;
    private final OrderTradePort orders;
    private final AftersaleAssembler assembler;
    private final OperationLogService audit;
    private final AppProperties properties;

    public SellerAftersaleServiceImpl(
            AftersaleRepository aftersales,
            OrderTradePort orders,
            AftersaleAssembler assembler,
            OperationLogService audit,
            AppProperties properties) {
        this.aftersales = aftersales;
        this.orders = orders;
        this.assembler = assembler;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AftersaleData> list(
            String status, String orderNo, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        AftersaleStatus statusFilter = parseStatus(status);

        String orderId = null;
        String orderNoFilter = TextRules.trimToNull(orderNo);
        if (orderNoFilter != null) {
            orderId = orders.findOrderIdByOrderNo(orderNoFilter).orElse(null);
            if (orderId == null) {
                // 订单号不存在时按「没有匹配」返回空页，而不是报 404：卖家在搜索框里敲错很常见。
                return PageResponse.of(List.of(), pageQuery.page(), pageQuery.pageSize(), 0);
            }
        }

        Page<Aftersale> result = aftersales.search(
                null, statusFilter, orderId, pageQuery.pageable());
        List<AftersaleData> items = result.getContent().stream()
                .map(assembler::summary)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AftersaleDetailData detail(String id) {
        return aftersales.findById(trim(id))
                .map(assembler::detail)
                .orElseThrow(() -> new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND));
    }

    @Override
    @Transactional
    public AftersaleData complete(String id, AftersaleCompleteRequest request) {
        AftersaleResult result = parseResult(request == null ? null : request.result());

        Aftersale aftersale = requireLocked(trim(id));
        if (aftersale.getStatus() != AftersaleStatus.PENDING) {
            throw new BusinessException(ErrorCode.AFTERSALE_STATUS_INVALID,
                    "aftersale " + aftersale.getAftersaleNo() + " is "
                            + aftersale.getStatus());
        }

        if (result.producesRefund()) {
            BigDecimal amount = reserveRefund(aftersale);
            aftersale.complete(result, amount, DatabaseTimeProvider.utcNow());
        } else {
            aftersale.complete(result, null, null);
        }

        aftersales.saveAndFlush(aftersale);
        audit.log(OperationLogService.AFTERSALE_COMPLETE,
                OperationLogService.TARGET_AFTERSALE, aftersale.getId());
        return assembler.summary(aftersale);
    }

    @Override
    @Transactional
    public AftersaleData cancel(String id) {
        Aftersale aftersale = requireLocked(trim(id));
        // 重复取消按成功返回，只为防抖；不写审计，避免日志里出现不存在的状态变更。
        if (aftersale.getStatus() == AftersaleStatus.CANCELED) {
            return assembler.summary(aftersale);
        }
        if (aftersale.getStatus() != AftersaleStatus.PENDING) {
            throw new BusinessException(ErrorCode.AFTERSALE_STATUS_INVALID,
                    "aftersale " + aftersale.getAftersaleNo() + " is already completed");
        }
        aftersale.cancel();
        aftersales.saveAndFlush(aftersale);
        audit.log(OperationLogService.AFTERSALE_CANCEL,
                OperationLogService.TARGET_AFTERSALE, aftersale.getId());
        return assembler.summary(aftersale);
    }

    /**
     * 退款额度：锁订单项 → 查该订单项是否已有退款类售后单 → 返回应退金额。
     *
     * <p>金额按订单项快照算（单价 × 数量），不接受前端传值：退多少是订单说了算。
     */
    private BigDecimal reserveRefund(Aftersale aftersale) {
        OrderItemSnapshot item = orders.lockOrderItem(aftersale.getOrderItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AFTERSALE_NOT_APPLICABLE,
                        "order item not found: " + aftersale.getOrderItemId()));
        long refunded = aftersales.countByOrderItemIdAndStatusAndResultIn(
                item.orderItemId(), AftersaleStatus.COMPLETED, REFUND_RESULTS);
        if (refunded > 0) {
            throw new BusinessException(ErrorCode.AFTERSALE_REFUND_DUPLICATED);
        }
        return item.amount();
    }

    private Aftersale requireLocked(String id) {
        return aftersales.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND));
    }

    private static AftersaleStatus parseStatus(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return AftersaleStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    /** 结果必填：空值与非法值都按 81005 返回，让前端能定位到「结果」这个字段。 */
    private static AftersaleResult parseResult(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.AFTERSALE_RESULT_REQUIRED);
        }
        try {
            return AftersaleResult.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AFTERSALE_RESULT_REQUIRED,
                    "unknown aftersale result: " + raw);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
