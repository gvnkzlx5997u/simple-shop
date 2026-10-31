package com.simpleshop.intention.service;

import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.dto.TradeFailureRequest;
import com.simpleshop.intention.dto.TradeResultData;

/**
 * 卖家交易裁决：进入交易、标记成功、标记失败。
 */
public interface SellerTradeService {

    IntentionEntryData enterTrade(String intentionId);

    TradeResultData markSuccess(String intentionId);

    TradeResultData markFailure(String intentionId, TradeFailureRequest request);
}
