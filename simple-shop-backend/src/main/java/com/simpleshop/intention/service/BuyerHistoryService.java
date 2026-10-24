package com.simpleshop.intention.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.intention.dto.BuyerHistoryEntryData;
import com.simpleshop.intention.repository.BuyerHistoryRow;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 买家历史：当前表已终态意向与历史意向表的并集，按下单时间倒序分页。
 * 只读，只含买家自己的终态记录。
 */
@Service
public class BuyerHistoryService {

    private final IntentionRepository intentions;
    private final AppProperties properties;

    public BuyerHistoryService(IntentionRepository intentions, AppProperties properties) {
        this.intentions = intentions;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<BuyerHistoryEntryData> history(
            String buyerId, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<BuyerHistoryEntryData> items = intentions
                .findBuyerHistoryPage(
                        buyerId,
                        pageQuery.pageSize(),
                        (long) (pageQuery.page() - 1) * pageQuery.pageSize())
                .stream()
                .map(BuyerHistoryService::entry)
                .toList();
        return PageResponse.of(items, pageQuery.page(), pageQuery.pageSize(),
                intentions.countBuyerHistory(buyerId));
    }

    private static BuyerHistoryEntryData entry(BuyerHistoryRow row) {
        return new BuyerHistoryEntryData(
                row.getGoodsName(),
                UtcIso8601.of(row.getCreateAt()),
                row.getResult(),
                UtcIso8601.of(row.getFinishAt()));
    }
}
