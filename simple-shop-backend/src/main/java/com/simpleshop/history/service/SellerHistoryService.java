package com.simpleshop.history.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.history.dto.GoodsHistoryDetailData;
import com.simpleshop.history.dto.GoodsHistorySummaryData;
import com.simpleshop.history.dto.HistoryDescriptionBlockData;
import com.simpleshop.history.dto.HistoryImageData;
import com.simpleshop.history.dto.IntentionHistoryEntryData;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.repository.GoodsHistoryRepository;
import com.simpleshop.history.repository.IntentionHistoryRepository;

/**
 * 卖家历史查询：历史商品列表与详情。全部只读，数据只来自历史表快照。
 */
@Service
public class SellerHistoryService {

    private final GoodsHistoryRepository goodsHistories;
    private final IntentionHistoryRepository intentionHistories;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SellerHistoryService(
            GoodsHistoryRepository goodsHistories,
            IntentionHistoryRepository intentionHistories,
            AppProperties properties,
            ObjectMapper objectMapper) {
        this.goodsHistories = goodsHistories;
        this.intentionHistories = intentionHistories;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsHistorySummaryData> list(Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        var result = goodsHistories.findAllByOrderByArchivedAtDescIdDesc(pageQuery.pageable());
        List<GoodsHistorySummaryData> items = result.getContent().stream()
                .map(SellerHistoryService::summary)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public GoodsHistoryDetailData detail(String id) {
        String historyId = id == null ? "" : id.trim();
        if (historyId.isEmpty()) {
            throw new BusinessException(ErrorCode.HISTORY_GOODS_NOT_FOUND);
        }
        GoodsHistory history = goodsHistories.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HISTORY_GOODS_NOT_FOUND));
        List<IntentionHistoryEntryData> intentions = intentionHistories
                .findByGoodsHistory_IdOrderByCreateAtAsc(history.getId()).stream()
                .map(SellerHistoryService::entry)
                .toList();
        return new GoodsHistoryDetailData(
                history.getId(),
                history.getName(),
                history.getPrice(),
                history.getCategoryL1Name(),
                history.getCategoryL2Name(),
                history.getResult().name(),
                parse(history.getDescriptionSnapshot(), HistoryDescriptionBlockData.class),
                parse(history.getImagesSnapshot(), HistoryImageData.class),
                UtcIso8601.of(history.getCreateAt()),
                UtcIso8601.of(history.getArchivedAt()),
                intentions);
    }

    private static GoodsHistorySummaryData summary(GoodsHistory history) {
        return new GoodsHistorySummaryData(
                history.getId(),
                history.getName(),
                history.getPrice(),
                history.getCategoryL1Name(),
                history.getCategoryL2Name(),
                history.getResult().name(),
                UtcIso8601.of(history.getCreateAt()),
                UtcIso8601.of(history.getArchivedAt()));
    }

    private static IntentionHistoryEntryData entry(IntentionHistory intention) {
        return new IntentionHistoryEntryData(
                intention.getId(),
                intention.getBuyerUsername(),
                intention.getBuyerPhone(),
                UtcIso8601.of(intention.getCreateAt()),
                intention.getResult().name(),
                intention.getFailType() == null ? null : intention.getFailType().name(),
                UtcIso8601.of(intention.getFinishAt()));
    }

    private <T> List<T> parse(String snapshotJson, Class<T> type) {
        try {
            return objectMapper.readValue(snapshotJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("history snapshot parse failed", exception);
        }
    }
}
