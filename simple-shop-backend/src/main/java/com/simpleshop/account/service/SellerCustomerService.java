package com.simpleshop.account.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.dto.CustomerDetailData;
import com.simpleshop.account.dto.CustomerSummaryData;
import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.repository.BuyerAccountRepository;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;

/**
 * 卖家客户查询：注册买家列表与详情，只读。购买历史经端口读取，不反向依赖。
 */
@Service
public class SellerCustomerService {

    private final BuyerAccountRepository buyers;
    private final AppProperties properties;
    private final CustomerPurchaseHistoryPort purchaseHistory;

    public SellerCustomerService(
            BuyerAccountRepository buyers,
            AppProperties properties,
            CustomerPurchaseHistoryPort purchaseHistory) {
        this.buyers = buyers;
        this.properties = properties;
        this.purchaseHistory = purchaseHistory;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryData> list(Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        Page<BuyerAccount> result =
                buyers.findAllByOrderByCreateAtDescIdDesc(pageQuery.pageable());
        List<CustomerSummaryData> items = result.getContent().stream()
                .map(SellerCustomerService::summary)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CustomerDetailData detail(String id, Integer page, Integer pageSize) {
        String buyerId = id == null ? "" : id.trim();
        if (buyerId.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        BuyerAccount buyer = buyers.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return new CustomerDetailData(
                buyer.getId(),
                buyer.getUsername(),
                buyer.getPhone(),
                buyer.getDefaultLocation(),
                UtcIso8601.of(buyer.getCreateAt()),
                purchaseHistory.history(buyer.getId(), page, pageSize));
    }

    private static CustomerSummaryData summary(BuyerAccount buyer) {
        return new CustomerSummaryData(
                buyer.getId(),
                buyer.getUsername(),
                buyer.getPhone(),
                buyer.getDefaultLocation(),
                UtcIso8601.of(buyer.getCreateAt()));
    }
}
