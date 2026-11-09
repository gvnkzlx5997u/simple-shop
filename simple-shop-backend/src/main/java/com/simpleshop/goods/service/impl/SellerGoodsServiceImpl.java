package com.simpleshop.goods.service.impl;

import com.simpleshop.goods.service.SellerGoodsService;
import com.simpleshop.goods.service.GoodsAssembler;
import com.simpleshop.goods.service.GoodsPublishPreparer;

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
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.api.UnfinishedOrderPort;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.goods.support.GoodsRules;
import com.simpleshop.inventory.api.StockPort;

/**
 * 卖家商品写操作。商品行负责状态，库存行独立加锁；状态联动时先锁商品再锁库存。
 *
 * <p>库存写入的三条口径：在售改 0 触发库存耗尽下架；库存耗尽下架改大于 0 自动上架；
 * 手动下架后库存只读，避免把库存改回正数误触发上架。
 *
 * <p>手动下架与手动改库存为 0 都会先问一句「还有未完成订单吗」：订单还没走完就下架，
 * 会让卖家后续无法完成交易。改成其他正数不拦，因为它不改变商品状态。
 */
@Service
public class SellerGoodsServiceImpl implements SellerGoodsService {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final GoodsContentBlockRepository contentBlocks;
    private final CategoryService categories;
    private final GoodsPublishPreparer preparer;
    private final GoodsAssembler assembler;
    private final StockPort stockPort;
    private final UnfinishedOrderPort unfinishedOrders;
    private final OperationLogService audit;

    public SellerGoodsServiceImpl(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            GoodsContentBlockRepository contentBlocks,
            CategoryService categories,
            GoodsPublishPreparer preparer,
            GoodsAssembler assembler,
            StockPort stockPort,
            UnfinishedOrderPort unfinishedOrders,
            OperationLogService audit) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.contentBlocks = contentBlocks;
        this.categories = categories;
        this.preparer = preparer;
        this.assembler = assembler;
        this.stockPort = stockPort;
        this.unfinishedOrders = unfinishedOrders;
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
                Goods.publish(name, price, category));
        stockPort.initialize(goods.getId(), stock);

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
        return assembler.detail(goods, images, blocks, stock);
    }

    @Transactional
    public GoodsDetailData updateStock(String id, Integer stockValue) {
        int stock = GoodsRules.adjustStock(stockValue);
        Goods goods = lockGoods(id);
        if (goods.isOnSale()) {
            applyOnSaleStock(goods, stock);
        } else if (goods.isRestockable()) {
            restock(goods, stock);
        } else {
            throw new BusinessException(ErrorCode.GOODS_STATUS_MISMATCH);
        }
        goodsRepository.saveAndFlush(goods);
        audit.log(OperationLogService.UPDATE_STOCK,
                OperationLogService.TARGET_GOODS, goods.getId());
        return detailOf(goods);
    }

    @Transactional
    public GoodsDetailData offShelf(String id) {
        Goods goods = lockGoods(id);
        if (!goods.isOnSale()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
        requireNoUnfinishedOrder(goods);
        goods.offShelf(OffShelfReason.MANUAL);
        goodsRepository.saveAndFlush(goods);
        audit.log(OperationLogService.OFF_SHELF_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        return detailOf(goods);
    }

    /** 所有状态都允许改挂类别：手动下架的商品否则会永久引用类别，导致类别删不掉。 */
    @Transactional
    public GoodsDetailData changeCategory(String id, String categoryId) {
        Goods goods = lockGoods(id);
        CategoryL2 category = requireLeafCategory(categoryId);
        if (!goods.getCategory().getId().equals(category.getId())) {
            goods.changeCategory(category);
            goodsRepository.saveAndFlush(goods);
            audit.log(OperationLogService.UPDATE_GOODS_CATEGORY,
                    OperationLogService.TARGET_GOODS, goods.getId());
        }
        return detailOf(goods);
    }

    /** 在售商品：改为 0 触发库存耗尽下架，改为其他正数只改库存。 */
    private void applyOnSaleStock(Goods goods, int stock) {
        if (stock > 0) {
            stockPort.setAvailable(goods.getId(), stock);
            return;
        }
        // 改 0 会连带下架，和手动下架同一条限制：有未完成订单时先拒绝，库存保持原值。
        requireNoUnfinishedOrder(goods);
        stockPort.setAvailable(goods.getId(), 0);
        goods.offShelf(OffShelfReason.STOCK_ZERO);
    }

    /** 订单还没走完就下架（或改成 0），卖家之后无法完成交易，因此整单条拦截。 */
    private void requireNoUnfinishedOrder(Goods goods) {
        if (unfinishedOrders.hasUnfinishedOrder(goods.getId())) {
            throw new BusinessException(ErrorCode.GOODS_HAS_UNFINISHED_ORDER);
        }
    }

    /** 库存耗尽下架的商品：补货即自动上架，原因与下架时间一起清空。 */
    private void restock(Goods goods, int stock) {
        if (stock == 0) {
            throw new BusinessException(
                    ErrorCode.STOCK_INVALID, "stock must be greater than 0 to go back on sale");
        }
        stockPort.setAvailable(goods.getId(), stock);
        goods.backOnSale();
    }

    private Goods lockGoods(String id) {
        return goodsRepository.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
    }

    private CategoryL2 requireLeafCategory(String rawCategoryId) {
        return categories.requireLeaf(GoodsRules.categoryId(rawCategoryId));
    }

    private GoodsDetailData detailOf(Goods goods) {
        return assembler.detail(
                goods,
                goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId()),
                contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId()),
                stockPort.availableOf(goods.getId()));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return id.trim();
    }
}
