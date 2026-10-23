package com.simpleshop.goods.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsSummaryData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;

/**
 * 卖家商品读操作：列表与详情。与写操作分开，便于控制事务边界。
 */
@Service
public class SellerGoodsQueryService {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final GoodsContentBlockRepository contentBlocks;
    private final GoodsQuerySupport querySupport;
    private final GoodsAssembler assembler;

    public SellerGoodsQueryService(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            GoodsContentBlockRepository contentBlocks,
            GoodsQuerySupport querySupport,
            GoodsAssembler assembler) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.contentBlocks = contentBlocks;
        this.querySupport = querySupport;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsSummaryData> list(
            String statusValue,
            String categoryId,
            String keyword,
            Integer page,
            Integer pageSize) {
        GoodsStatus status = querySupport.status(statusValue);
        GoodsQuerySupport.CategoryFilter category = querySupport.category(categoryId);
        PageQuery pageQuery = querySupport.page(page, pageSize);
        Page<Goods> result = goodsRepository.search(
                status,
                category.id(),
                category.level(),
                querySupport.keyword(keyword),
                pageQuery.pageable());
        Map<String, String> mainImages = mainImages(result.getContent());
        List<GoodsSummaryData> items = result.getContent().stream()
                .map(goods -> assembler.summary(goods, mainImages.get(goods.getId())))
                .toList();
        return PageResponse.of(items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public GoodsDetailData detail(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        Goods goods = goodsRepository.findDetailById(id.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        return assembler.detail(
                goods,
                goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId()),
                contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId()));
    }

    private Map<String, String> mainImages(List<Goods> goods) {
        if (goods.isEmpty()) {
            return Map.of();
        }
        List<String> ids = goods.stream().map(Goods::getId).toList();
        Map<String, String> result = new HashMap<>();
        for (GoodsImage image : goodsImages.findByGoods_IdInOrderBySortNoAsc(ids)) {
            if (image.isMain()) {
                result.putIfAbsent(image.getGoods().getId(), image.getUrl());
            }
        }
        return result;
    }
}
