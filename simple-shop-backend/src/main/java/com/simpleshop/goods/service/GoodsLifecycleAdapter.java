package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.goods.api.GoodsArchiveSnapshot;
import com.simpleshop.goods.api.GoodsLifecyclePort;
import com.simpleshop.goods.api.GoodsTradeView;
import com.simpleshop.goods.api.TradingStart;
import com.simpleshop.goods.dto.DescriptionBlockData;
import com.simpleshop.goods.dto.GoodsImageData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;

/**
 * 商品生命周期端口实现：状态与归档只在本类内改商品实体。
 * 调用方必须已开启事务；锁顺序由调用方保证从商品行开始。
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class GoodsLifecycleAdapter implements GoodsLifecyclePort {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final GoodsContentBlockRepository contentBlocks;
    private final ObjectMapper objectMapper;

    public GoodsLifecycleAdapter(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            GoodsContentBlockRepository contentBlocks,
            ObjectMapper objectMapper) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.contentBlocks = contentBlocks;
        this.objectMapper = objectMapper;
    }

    @Override
    public void requireOnSaleForNewIntention(String goodsId) {
        Goods goods = goodsRepository.findWithSharedLockById(requireId(goodsId))
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        if (!goods.isOnSale()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
    }

    @Override
    public TradingStart lockForTrading(String goodsId) {
        Goods goods = lock(goodsId);
        if (goods.getStatus() == GoodsStatus.FROZEN) {
            return TradingStart.ALREADY_TRADING;
        }
        if (!goods.isOnSale()) {
            return TradingStart.NOT_ON_SALE;
        }
        return TradingStart.STARTED;
    }

    @Override
    public void startTrading(String goodsId) {
        Goods goods = lock(goodsId);
        if (!goods.isOnSale()) {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        goods.changeStatus(GoodsStatus.FROZEN);
        goodsRepository.saveAndFlush(goods);
    }

    @Override
    public GoodsTradeView requireTrading(String goodsId) {
        Goods goods = lock(goodsId);
        if (goods.getStatus() != GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        return new GoodsTradeView(goods.getId(), goods.getName(), goods.getStatus());
    }

    @Override
    public void backToOnSale(String goodsId) {
        Goods goods = lock(goodsId);
        if (goods.getStatus() != GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        goods.changeStatus(GoodsStatus.ON_SALE);
        goodsRepository.saveAndFlush(goods);
    }

    @Override
    public GoodsArchiveSnapshot snapshotForArchive(String goodsId, int finalStock) {
        Goods goods = lock(goodsId);
        List<GoodsImage> images = goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId());
        List<GoodsContentBlock> blocks =
                contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId());
        return new GoodsArchiveSnapshot(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                finalStock,
                goods.getCategory().getL1().getName(),
                goods.getCategory().getName(),
                toJson(descriptionSnapshot(blocks)),
                toJson(imageSnapshot(images)),
                goods.getCreateAt());
    }

    @Override
    public void removeArchived(String goodsId) {
        goodsRepository.deleteRowById(requireId(goodsId));
    }

    private Goods lock(String goodsId) {
        return goodsRepository.findWithLockById(requireId(goodsId))
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
    }

    private List<DescriptionBlockData> descriptionSnapshot(List<GoodsContentBlock> blocks) {
        List<DescriptionBlockData> data = new ArrayList<>(blocks.size());
        for (GoodsContentBlock block : blocks) {
            data.add(new DescriptionBlockData(
                    block.getBlockType().name(), block.getTextContent(), block.getMediaUrl()));
        }
        return data;
    }

    private List<GoodsImageData> imageSnapshot(List<GoodsImage> images) {
        List<GoodsImageData> data = new ArrayList<>(images.size());
        for (GoodsImage image : images) {
            data.add(new GoodsImageData(image.getUrl(), image.isMain(), image.getSortNo()));
        }
        return data;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("archive snapshot serialization failed", exception);
        }
    }

    private static String requireId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return value;
    }
}
