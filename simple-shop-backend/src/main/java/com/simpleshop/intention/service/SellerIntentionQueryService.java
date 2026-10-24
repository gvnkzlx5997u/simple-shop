package com.simpleshop.intention.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.service.SellerGoodsQueryService;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 卖家意向名单查询：按商品、按队列序号升序分页。任何商品状态都允许查看意向。
 */
@Service
public class SellerIntentionQueryService {

    private final SellerGoodsQueryService goodsQuery;
    private final IntentionRepository intentions;
    private final AppProperties properties;

    public SellerIntentionQueryService(
            SellerGoodsQueryService goodsQuery,
            IntentionRepository intentions,
            AppProperties properties) {
        this.goodsQuery = goodsQuery;
        this.intentions = intentions;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<IntentionEntryData> list(
            String goodsId, String statusValue, Integer page, Integer pageSize) {
        Goods goods = goodsQuery.requireGoods(goodsId);
        IntentionStatus status = status(statusValue);
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<IntentionStatus> statuses =
                status == null ? List.of(IntentionStatus.values()) : List.of(status);
        Page<Intention> result = intentions.findByGoods_IdAndStatusInOrderByQueueOrderAsc(
                goods.getId(), statuses, pageQuery.pageable());
        List<IntentionEntryData> items = result.getContent().stream()
                .map(SellerIntentionQueryService::entry)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    /** 单条意向的名单条目映射；交易裁决服务在状态变更后复用。须在读取意向的同一事务内调用。 */
    public IntentionEntryData entryOf(Intention intention) {
        return entry(intention);
    }

    private static IntentionEntryData entry(Intention intention) {
        return new IntentionEntryData(
                intention.getId(),
                intention.getQueueOrder(),
                intention.getStatus().name(),
                intention.getFailType() == null ? null : intention.getFailType().name(),
                intention.getBuyer().getUsername(),
                intention.getBuyer().getPhone(),
                intention.getBuyer().getDefaultLocation(),
                UtcIso8601.of(intention.getCreateAt()),
                UtcIso8601.of(intention.getFinishAt()));
    }

    private static IntentionStatus status(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return IntentionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }
}
