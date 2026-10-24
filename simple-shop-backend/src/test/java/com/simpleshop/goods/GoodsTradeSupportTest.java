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
import com.simpleshop.goods.dto.DescriptionBlockData;
import com.simpleshop.goods.dto.GoodsArchiveSnapshot;
import com.simpleshop.goods.dto.GoodsImageData;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.service.GoodsTradeSupport;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.testing.DataLayerTestBase;

class GoodsTradeSupportTest extends DataLayerTestBase {

    @Autowired
    private GoodsTradeSupport tradeSupport;

    @Test
    @DisplayName("lockById 悲观锁加载商品；缺失或空白 ID 返回商品不存在")
    void locksGoodsById() {
        Goods goods = createGoods(createCategory());

        Goods locked = tradeSupport.lockById(goods.getId());
        assertThat(locked.getId()).isEqualTo(goods.getId());
        assertThat(locked.getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        assertThatThrownBy(() -> tradeSupport.lockById(unique("G-missing")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
        assertThatThrownBy(() -> tradeSupport.lockById("  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    @DisplayName("buildArchiveSnapshot 固化商品当前值与描述、图片 JSON 快照")
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
        goods.changeStock(0);
        flushAndClear();

        Goods locked = tradeSupport.lockById(goods.getId());
        GoodsArchiveSnapshot snapshot = tradeSupport.buildArchiveSnapshot(locked);

        assertThat(snapshot.id()).isEqualTo(goods.getId());
        assertThat(snapshot.name()).isEqualTo(goods.getName());
        assertThat(snapshot.price()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(snapshot.stock()).isZero();
        assertThat(snapshot.categoryL1Name()).isEqualTo(l1Name);
        assertThat(snapshot.categoryL2Name()).isEqualTo(l2Name);
        assertThat(snapshot.createAt()).isEqualTo(locked.getCreateAt());

        List<DescriptionBlockData> blocks = objectMapper.readValue(
                snapshot.descriptionSnapshot(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DescriptionBlockData.class));
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).type()).isEqualTo("TEXT");
        assertThat(blocks.get(0).text()).isEqualTo("描述文本");
        assertThat(blocks.get(1).type()).isEqualTo("VIDEO");
        assertThat(blocks.get(1).url()).isEqualTo("/media/description/v.mp4");

        List<GoodsImageData> images = objectMapper.readValue(
                snapshot.imagesSnapshot(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, GoodsImageData.class));
        assertThat(images).hasSize(2);
        assertThat(images.get(0).main()).isTrue();
        assertThat(images.get(0).sortNo()).isEqualTo(1);
        assertThat(images.get(1).main()).isFalse();
    }

    @Test
    @DisplayName("removeArchived 删除商品行并级联清理图片、描述块、队列序号行")
    void removesGoodsAndCascadesChildren() {
        Goods goods = createGoods(createCategory());
        goodsImages.saveAndFlush(GoodsImage.add(goods, "/media/goods/main.jpg", true, 1));
        contentBlocks.saveAndFlush(GoodsContentBlock.text(goods, "描述", 1));
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));

        tradeSupport.removeArchived(goods.getId());
        flushAndClear();

        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId())).isEmpty();
        assertThat(contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId())).isEmpty();
        assertThat(queueSequences.findById(goods.getId())).isEmpty();
    }

    @Test
    @DisplayName("意向行尚未删除时 removeArchived 被外键拒绝")
    void refusesRemoveWhenIntentionsRemain() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        intentions.saveAndFlush(Intention.submit(goods, buyer, 1));

        assertThatThrownBy(() -> tradeSupport.removeArchived(goods.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
