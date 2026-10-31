package com.simpleshop.intention.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.ClientIp;
import com.simpleshop.common.auth.CurrentUser;
import com.simpleshop.intention.dto.IntentionAcceptedData;
import com.simpleshop.intention.dto.SubmitIntentionRequest;
import com.simpleshop.intention.service.BuyerIntentionService;

/**
 * 买家购买意向：仅提交一种写操作，无修改、无撤销。
 */
@RestController
@RequestMapping("/api/buyer/intentions")
@Tag(name = "买家意向")
@SecurityRequirement(name = "bearerAuth")
public class BuyerIntentionController {

    private final BuyerIntentionService intentionService;

    public BuyerIntentionController(BuyerIntentionService intentionService) {
        this.intentionService = intentionService;
    }

    @PostMapping
    @Operation(summary = "提交购买意向；回执不含排队位次")
    public ApiResponse<IntentionAcceptedData> submit(
            @Valid @RequestBody SubmitIntentionRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(intentionService.submit(
                request, CurrentUser.require(httpRequest), ClientIp.of(httpRequest)));
    }
}
