package com.simpleshop.history.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.api.CustomerPurchaseEntryData;
import com.simpleshop.account.api.CustomerPurchaseHistoryPort;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.history.repository.CustomerPurchaseRow;
import com.simpleshop.history.repository.HistoryQueryRepository;

/**
 * 客户购买历史端口实现：与买家历史同构，卖家视角多出失败原因与商品 ID。
 */
@Component
public class CustomerPurchaseHistoryAdapter implements CustomerPurchaseHistoryPort {

    private final HistoryQueryRepository histories;
    private final AppProperties properties;

    public CustomerPurchaseHistoryAdapter(
            HistoryQueryRepository histories, AppProperties properties) {
        this.histories = histories;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerPurchaseEntryData> history(
            String buyerId, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<CustomerPurchaseEntryData> items = histories
                .findCustomerPurchasePage(
                        buyerId,
                        pageQuery.pageSize(),
                        (long) (pageQuery.page() - 1) * pageQuery.pageSize())
                .stream()
                .map(CustomerPurchaseHistoryAdapter::entry)
                .toList();
        return PageResponse.of(items, pageQuery.page(), pageQuery.pageSize(),
                histories.countBuyerHistory(buyerId));
    }

    private static CustomerPurchaseEntryData entry(CustomerPurchaseRow row) {
        return new CustomerPurchaseEntryData(
                row.getGoodsId(),
                row.getGoodsName(),
                UtcIso8601.of(row.getCreateAt()),
                row.getResult(),
                row.getFailType(),
                UtcIso8601.of(row.getFinishAt()));
    }
}
