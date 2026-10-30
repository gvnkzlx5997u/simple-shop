package com.simpleshop.history.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.history.dto.BuyerHistoryEntryData;
import com.simpleshop.history.repository.BuyerHistoryRow;
import com.simpleshop.history.repository.HistoryQueryRepository;
import com.simpleshop.history.service.BuyerHistoryService;

/**
 * 买家历史：当前表已终态意向与历史意向表的并集，按下单时间倒序分页。
 */
@Service
public class BuyerHistoryServiceImpl implements BuyerHistoryService {

    private final HistoryQueryRepository histories;
    private final AppProperties properties;

    public BuyerHistoryServiceImpl(HistoryQueryRepository histories, AppProperties properties) {
        this.histories = histories;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BuyerHistoryEntryData> history(
            String buyerId, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<BuyerHistoryEntryData> items = histories
                .findBuyerHistoryPage(
                        buyerId,
                        pageQuery.pageSize(),
                        (long) (pageQuery.page() - 1) * pageQuery.pageSize())
                .stream()
                .map(BuyerHistoryServiceImpl::entry)
                .toList();
        return PageResponse.of(items, pageQuery.page(), pageQuery.pageSize(),
                histories.countBuyerHistory(buyerId));
    }

    private static BuyerHistoryEntryData entry(BuyerHistoryRow row) {
        return new BuyerHistoryEntryData(
                row.getGoodsName(),
                UtcIso8601.of(row.getCreateAt()),
                row.getResult(),
                UtcIso8601.of(row.getFinishAt()));
    }
}
