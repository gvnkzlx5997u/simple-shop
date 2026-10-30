package com.simpleshop.account.service;

import com.simpleshop.account.dto.CustomerDetailData;
import com.simpleshop.account.dto.CustomerSummaryData;
import com.simpleshop.common.api.PageResponse;

/**
 * 卖家客户查询：注册买家列表与详情，只读。
 */
public interface SellerCustomerService {

    PageResponse<CustomerSummaryData> list(Integer page, Integer pageSize);

    CustomerDetailData detail(String id, Integer page, Integer pageSize);
}
