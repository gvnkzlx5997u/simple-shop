package com.simpleshop.intention.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.intention.repository.IntentionRepository;
import com.simpleshop.intention.service.IntentionEntryAssembler;
import com.simpleshop.intention.service.SellerIntentionQueryService;

/**
 * 卖家意向名单查询：按商品、按队列序号升序分页。任何商品状态都允许查看意向。
 */
@Service
public class SellerIntentionQueryServiceImpl implements SellerIntentionQueryService {

    private final GoodsQueryPort goodsQuery;
    private final IntentionRepository intentions;
    private final AppProperties properties;

    public SellerIntentionQueryServiceImpl(
            GoodsQueryPort goodsQuery,
            IntentionRepository intentions,
            AppProperties properties) {
        this.goodsQuery = goodsQuery;
        this.intentions = intentions;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<IntentionEntryData> list(
            String goodsId, IntentionStatus status, Integer page, Integer pageSize) {
        GoodsSnapshot goods = goodsQuery.require(goodsId);
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<IntentionStatus> statuses =
                status == null ? List.of(IntentionStatus.values()) : List.of(status);
        Page<Intention> result = intentions.findByGoodsIdAndStatusInOrderByQueueOrderAsc(
                goods.id(), statuses, pageQuery.pageable());
        List<IntentionEntryData> items = result.getContent().stream()
                .map(IntentionEntryAssembler::entry)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }
}
