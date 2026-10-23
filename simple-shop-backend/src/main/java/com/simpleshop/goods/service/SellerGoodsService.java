package com.simpleshop.goods.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.category.service.CategoryService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsPublishRequest;
import com.simpleshop.goods.dto.ReListRequest;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.goods.support.GoodsRules;

/**
 * 卖家商品写操作。商品行是同一商品写操作的聚合锁；库存、下架与改挂都先锁商品。
 */
@Service
public class SellerGoodsService {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final GoodsContentBlockRepository contentBlocks;
    private final GoodsQueuePort queuePort;
    private final CategoryService categories;
    private final GoodsPublishPreparer preparer;
    private final GoodsAssembler assembler;
    private final OperationLogService audit;

    public SellerGoodsService(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            GoodsContentBlockRepository contentBlocks,
            GoodsQueuePort queuePort,
            CategoryService categories,
            GoodsPublishPreparer preparer,
            GoodsAssembler assembler,
            OperationLogService audit) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.contentBlocks = contentBlocks;
        this.queuePort = queuePort;
        this.categories = categories;
        this.preparer = preparer;
        this.assembler = assembler;
        this.audit = audit;
    }

    @Transactional
    public GoodsDetailData publish(GoodsPublishRequest request) {
        String name = GoodsRules.name(request.name());
        BigDecimal price = GoodsRules.price(request.price());
        int stock = GoodsRules.publishStock(request.stock());
        CategoryL2 category = requireLeafCategory(request.categoryId());
        GoodsPublishPreparer.Prepared prepared = preparer.prepare(
                request.mainImage(), request.galleryImages(), request.description());

        Goods goods = goodsRepository.saveAndFlush(
                Goods.publish(name, price, stock, category));
        queuePort.initialize(goods.getId());

        List<GoodsPublishPreparer.ImageInput> imageInputs = prepared.images();
        List<GoodsImage> images = new ArrayList<>(imageInputs.size());
        for (int index = 0; index < imageInputs.size(); index++) {
            GoodsPublishPreparer.ImageInput input = imageInputs.get(index);
            images.add(GoodsImage.add(goods, input.url(), input.main(), index + 1));
        }

        List<GoodsPublishPreparer.BlockInput> blockInputs = prepared.blocks();
        List<GoodsContentBlock> blocks = new ArrayList<>(blockInputs.size());
        for (int index = 0; index < blockInputs.size(); index++) {
            GoodsPublishPreparer.BlockInput input = blockInputs.get(index);
            int sortNo = index + 1;
            blocks.add(switch (input.type()) {
                case TEXT -> GoodsContentBlock.text(goods, input.text(), sortNo);
                case IMAGE -> GoodsContentBlock.image(goods, input.url(), sortNo);
                case VIDEO -> GoodsContentBlock.video(goods, input.url(), sortNo);
            });
        }

        if (!images.isEmpty()) {
            goodsImages.saveAll(images);
        }
        if (!blocks.isEmpty()) {
            contentBlocks.saveAll(blocks);
        }
        goodsImages.flush();
        contentBlocks.flush();
        audit.log(OperationLogService.PUBLISH_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        return assembler.detail(goods, images, blocks);
    }

    @Transactional
    public GoodsDetailData updateStock(String id, Integer stockValue) {
        int stock = GoodsRules.adjustStock(stockValue);
        Goods goods = lockGoods(id);
        switch (goods.getStatus()) {
            case FROZEN -> throw new BusinessException(ErrorCode.GOODS_FROZEN);
            case OFF_MANUAL -> throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
            case ON_SALE -> {
                if (stock == 0) {
                    requireEmptyQueue(goods.getId());
                    goods.changeStock(0);
                    goods.changeStatus(GoodsStatus.OFF_ZERO);
                } else {
                    goods.changeStock(stock);
                }
            }
            case OFF_ZERO -> {
                if (stock == 0) {
                    throw new BusinessException(
                            ErrorCode.STOCK_INVALID, "stock must be greater than 0 to re-list");
                }
                goods.changeStock(stock);
                goods.changeStatus(GoodsStatus.ON_SALE);
            }
        }
        goodsRepository.saveAndFlush(goods);
        audit.log(OperationLogService.UPDATE_STOCK,
                OperationLogService.TARGET_GOODS, goods.getId());
        return detailOf(goods);
    }

    @Transactional
    public GoodsDetailData offShelf(String id) {
        Goods goods = lockGoods(id);
        if (goods.getStatus() == GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_FROZEN);
        }
        if (goods.getStatus() != GoodsStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
        requireEmptyQueue(goods.getId());
        goods.changeStatus(GoodsStatus.OFF_MANUAL);
        goodsRepository.saveAndFlush(goods);
        audit.log(OperationLogService.OFF_SHELF_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        return detailOf(goods);
    }

    @Transactional
    public GoodsDetailData reList(String id, ReListRequest request) {
        Goods goods = lockGoods(id);
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED);
        }
        if (goods.getStatus() == GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_FROZEN);
        }
        if (goods.getStatus() != GoodsStatus.OFF_MANUAL) {
            throw new BusinessException(ErrorCode.GOODS_NOT_OFF_SHELF);
        }
        if (goods.getStock() < 1) {
            throw new BusinessException(ErrorCode.STOCK_INVALID);
        }
        goods.changeStatus(GoodsStatus.ON_SALE);
        goodsRepository.saveAndFlush(goods);
        audit.log(OperationLogService.RE_LIST_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        return detailOf(goods);
    }

    @Transactional
    public GoodsDetailData changeCategory(String id, String categoryId) {
        Goods goods = lockGoods(id);
        if (goods.getStatus() == GoodsStatus.FROZEN) {
            throw new BusinessException(ErrorCode.GOODS_FROZEN);
        }
        CategoryL2 category = requireLeafCategory(categoryId);
        if (!goods.getCategory().getId().equals(category.getId())) {
            goods.changeCategory(category);
            goodsRepository.saveAndFlush(goods);
            audit.log(OperationLogService.UPDATE_GOODS_CATEGORY,
                    OperationLogService.TARGET_GOODS, goods.getId());
        }
        return detailOf(goods);
    }

    private Goods lockGoods(String id) {
        return goodsRepository.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
    }

    private CategoryL2 requireLeafCategory(String rawCategoryId) {
        return categories.requireLeaf(GoodsRules.categoryId(rawCategoryId));
    }

    private void requireEmptyQueue(String goodsId) {
        if (queuePort.hasActiveIntentions(goodsId)) {
            throw new BusinessException(ErrorCode.QUEUE_NOT_EMPTY);
        }
    }

    private GoodsDetailData detailOf(Goods goods) {
        return assembler.detail(
                goods,
                goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId()),
                contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId()));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return id.trim();
    }
}
