package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.goods.dto.CategoryRefData;
import com.simpleshop.goods.dto.DescriptionBlockData;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsImageData;
import com.simpleshop.goods.dto.GoodsSummaryData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 商品实体到响应 DTO 的映射，列表只取主图，详情取全部图片与描述块。
 */
@Component
public class GoodsAssembler {

    public CategoryRefData category(Goods goods) {
        return new CategoryRefData(
                goods.getCategory().getL1().getId(),
                goods.getCategory().getL1().getName(),
                goods.getCategory().getId(),
                goods.getCategory().getName());
    }

    public GoodsSummaryData summary(Goods goods, String mainImageUrl) {
        return new GoodsSummaryData(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                goods.getStock(),
                goods.getStatus(),
                mainImageUrl,
                category(goods),
                UtcIso8601.of(goods.getCreateAt()),
                UtcIso8601.of(goods.getUpdateAt()));
    }

    public GoodsDetailData detail(
            Goods goods, List<GoodsImage> images, List<GoodsContentBlock> blocks) {
        List<GoodsImage> safeImages = images == null ? List.of() : images;
        List<GoodsContentBlock> safeBlocks = blocks == null ? List.of() : blocks;
        List<GoodsImageData> imageData = new ArrayList<>(safeImages.size());
        String mainImageUrl = null;
        for (GoodsImage image : safeImages) {
            imageData.add(new GoodsImageData(image.getUrl(), image.isMain(), image.getSortNo()));
            if (image.isMain() && mainImageUrl == null) {
                mainImageUrl = image.getUrl();
            }
        }
        List<DescriptionBlockData> description = new ArrayList<>(safeBlocks.size());
        for (GoodsContentBlock block : safeBlocks) {
            description.add(new DescriptionBlockData(
                    block.getBlockType().name(),
                    block.getTextContent(),
                    block.getMediaUrl()));
        }
        return new GoodsDetailData(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                goods.getStock(),
                goods.getStatus(),
                category(goods),
                mainImageUrl,
                imageData,
                description,
                UtcIso8601.of(goods.getCreateAt()),
                UtcIso8601.of(goods.getUpdateAt()),
                goods.getStatus() == GoodsStatus.ON_SALE && goods.getStock() > 0);
    }
}
