package com.simpleshop.intention.service;

import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.intention.dto.IntentionAcceptedData;
import com.simpleshop.intention.dto.SubmitIntentionRequest;

/**
 * 买家提交购买意向。
 */
public interface BuyerIntentionService {

    IntentionAcceptedData submit(
            SubmitIntentionRequest request, AuthPrincipal buyerPrincipal, String clientKey);
}
