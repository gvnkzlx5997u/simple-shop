package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.goods.dto.DescriptionBlockData;
import com.simpleshop.goods.dto.GoodsArchiveSnapshot;
import com.simpleshop.goods.dto.GoodsImageData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;

/**
 * 交易流程对当前商品的支撑操作：悲观锁加载、归档快照构建、归档后的当前行删除。
 * 供 intention 侧交易服务调用；商品行是同一商品写操作的聚合锁，加锁顺序必须从商品行开始。
 */
@Component
public class GoodsTradeSupport {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final GoodsContentBlockRepository contentBlocks;
    private final ObjectMapper objectMapper;

    public GoodsTradeSupport(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            GoodsContentBlockRepository contentBlocks,
            ObjectMapper objectMapper) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.contentBlocks = contentBlocks;
        this.objectMapper = objectMapper;
    }

    /** 悲观锁加载商品，行锁保持到调用方事务提交；不校验状态，状态守卫由调用方完成。 */
    public Goods lockById(String id) {
        String goodsId = id == null ? "" : id.trim();
        if (goodsId.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return goodsRepository.findWithLockById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
    }

    /** 归档快照：类别名、描述块、图片按当前值固化为 JSON，历史不再引用当前类别与子表。 */
    public GoodsArchiveSnapshot buildArchiveSnapshot(Goods goods) {
        List<GoodsImage> images = goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId());
        List<GoodsContentBlock> blocks = contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId());
        return new GoodsArchiveSnapshot(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                goods.getStock(),
                goods.getCategory().getL1().getName(),
                goods.getCategory().getName(),
                toJson(descriptionSnapshot(blocks)),
                toJson(imageSnapshot(images)),
                goods.getCreateAt());
    }

    /**
     * 归档事务内删除当前商品行。前置条件：该商品全部意向行已在本事务内删除
     * （意向外键 RESTRICT）；图片、描述块、队列序号行由数据库级联删除。
     * 批量删除绕过一级缓存，删除后不得再访问该商品实体。
     */
    public void removeArchived(String goodsId) {
        goodsRepository.deleteRowById(goodsId);
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
}
