package com.simpleshop.intention.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.enums.IntentionStatus;

/**
 * 卖家意向名单查询：按商品、按队列序号升序分页。
 */
public interface SellerIntentionQueryService {

    PageResponse<IntentionEntryData> list(
            String goodsId, IntentionStatus status, Integer page, Integer pageSize);
}
