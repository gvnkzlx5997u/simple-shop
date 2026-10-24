package com.simpleshop.intention.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.dto.CustomerPurchaseEntryData;
import com.simpleshop.account.service.CustomerPurchaseHistoryPort;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.intention.repository.CustomerPurchaseRow;
import com.simpleshop.intention.repository.IntentionRepository;

/**
 * 客户购买历史端口实现：并集查询与买家历史同构，只是卖家视角多出失败原因与商品 ID。
 * 分页在 UNION 之后。
 */
@Component
public class CustomerPurchaseHistoryAdapter implements CustomerPurchaseHistoryPort {

    private final IntentionRepository intentions;
    private final AppProperties properties;

    public CustomerPurchaseHistoryAdapter(
            IntentionRepository intentions, AppProperties properties) {
        this.intentions = intentions;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerPurchaseEntryData> history(
            String buyerId, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        List<CustomerPurchaseEntryData> items = intentions
                .findCustomerPurchasePage(
                        buyerId,
                        pageQuery.pageSize(),
                        (long) (pageQuery.page() - 1) * pageQuery.pageSize())
                .stream()
                .map(CustomerPurchaseHistoryAdapter::entry)
                .toList();
        return PageResponse.of(items, pageQuery.page(), pageQuery.pageSize(),
                intentions.countBuyerHistory(buyerId));
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
