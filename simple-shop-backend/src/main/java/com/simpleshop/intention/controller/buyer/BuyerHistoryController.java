package com.simpleshop.intention.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.auth.CurrentUser;
import com.simpleshop.intention.dto.BuyerHistoryEntryData;
import com.simpleshop.intention.service.BuyerHistoryService;

/**
 * 买家历史：自己的终态意向，只读。
 */
@RestController
@Tag(name = "买家历史")
@SecurityRequirement(name = "bearerAuth")
public class BuyerHistoryController {

    private final BuyerHistoryService historyService;

    public BuyerHistoryController(BuyerHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/api/buyer/history")
    @Operation(summary = "我的历史；仅终态意向，按下单时间倒序分页")
    public ApiResponse<PageResponse<BuyerHistoryEntryData>> history(
            HttpServletRequest httpRequest,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(historyService.history(
                CurrentUser.require(httpRequest).subjectId(), page, pageSize));
    }
}
