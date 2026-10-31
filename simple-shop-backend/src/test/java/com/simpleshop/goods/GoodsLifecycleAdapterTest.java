package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.goods.api.GoodsArchiveSnapshot;
import com.simpleshop.goods.api.GoodsLifecyclePort;
import com.simpleshop.goods.api.TradingStart;
import com.simpleshop.goods.dto.DescriptionBlockData;
import com.simpleshop.goods.dto.GoodsImageData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.testing.DataLayerTestBase;

class GoodsLifecycleAdapterTest extends DataLayerTestBase {

    @Autowired
    private GoodsLifecyclePort goodsLifecycle;

    @Test
    @DisplayName("requireTrading 缺失商品或空白 ID 返回商品不存在")
    void requireTradingRejectsMissingGoods() {
        assertThatThrownBy(() -> goodsLifecycle.requireTrading(unique("G-missing")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
        assertThatThrownBy(() -> goodsLifecycle.requireTrading("  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    @DisplayName("enterTrading：在售→交易中；重复进入返回 ALREADY_TRADING；下架返回 NOT_ON_SALE")
    void enterTradingTransitions() {
        Goods goods = createGoods(createCategory());
        assertThat(goodsLifecycle.lockForTrading(goods.getId())).isEqualTo(TradingStart.STARTED);
        goodsLifecycle.startTrading(goods.getId());
        flushAndClear();
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.FROZEN);
        assertThat(goodsLifecycle.lockForTrading(goods.getId()))
                .isEqualTo(TradingStart.ALREADY_TRADING);

        Goods offShelf = createGoods(createCategory());
        offShelf.changeStatus(GoodsStatus.OFF_MANUAL);
        goodsRepository.saveAndFlush(offShelf);
        assertThat(goodsLifecycle.lockForTrading(offShelf.getId()))
                .isEqualTo(TradingStart.NOT_ON_SALE);
    }

    @Test
    @DisplayName("snapshotForArchive 固化最终库存、类别与描述/图片 JSON 快照")
    void buildsArchiveSnapshot() throws Exception {
        CategoryL2 category = createCategory();
        String l1Name = category.getL1().getName();
        String l2Name = category.getName();
        Goods goods = createGoods(category);
        goodsImages.saveAllAndFlush(List.of(
                GoodsImage.add(goods, "/media/goods/main.jpg", true, 1),
                GoodsImage.add(goods, "/media/goods/gallery.jpg", false, 2)));
        contentBlocks.saveAllAndFlush(List.of(
                GoodsContentBlock.text(goods, "描述文本", 1),
                GoodsContentBlock.video(goods, "/media/description/v.mp4", 2)));
        goodsLifecycle.lockForTrading(goods.getId());
        goodsLifecycle.startTrading(goods.getId());
        flushAndClear();

        GoodsArchiveSnapshot snapshot = goodsLifecycle.snapshotForArchive(goods.getId(), 0);

        assertThat(snapshot.id()).isEqualTo(goods.getId());
        assertThat(snapshot.name()).isEqualTo(goods.getName());
        assertThat(snapshot.price()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(snapshot.finalStock()).isZero();
        assertThat(snapshot.categoryL1Name()).isEqualTo(l1Name);
        assertThat(snapshot.categoryL2Name()).isEqualTo(l2Name);

        List<DescriptionBlockData> blocks = objectMapper.readValue(
                snapshot.descriptionSnapshot(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DescriptionBlockData.class));
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).type()).isEqualTo("TEXT");
        assertThat(blocks.get(0).text()).isEqualTo("描述文本");
        assertThat(blocks.get(1).type()).isEqualTo("VIDEO");

        List<GoodsImageData> images = objectMapper.readValue(
                snapshot.imagesSnapshot(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, GoodsImageData.class));
        assertThat(images).hasSize(2);
        assertThat(images.get(0).main()).isTrue();
        assertThat(images.get(1).main()).isFalse();
    }

    @Test
    @DisplayName("removeArchived 删除商品行并级联清理图片、描述块、库存与队列序号行")
    void removesGoodsAndCascadesChildren() {
        Goods goods = createGoods(createCategory());
        goodsImages.saveAndFlush(GoodsImage.add(goods, "/media/goods/main.jpg", true, 1));
        contentBlocks.saveAndFlush(GoodsContentBlock.text(goods, "描述", 1));
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));

        goodsLifecycle.removeArchived(goods.getId());
        flushAndClear();

        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId())).isEmpty();
        assertThat(contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId())).isEmpty();
        assertThat(queueSequences.findById(goods.getId())).isEmpty();
        assertThat(stockItems.findById(goods.getId())).isEmpty();
    }

    @Test
    @DisplayName("意向行尚未删除时 removeArchived 被外键拒绝")
    void refusesRemoveWhenIntentionsRemain() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        intentions.saveAndFlush(Intention.submit(goods.getId(), buyer, 1));

        assertThatThrownBy(() -> goodsLifecycle.removeArchived(goods.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
