package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.ContentBlockType;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.testing.DataLayerTestBase;

class GoodsDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("商品、图片、描述块可以按顺序持久化并读回")
    void persistsGoodsWithImagesAndContentBlocks() {
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);

        goodsImages.saveAllAndFlush(List.of(
                GoodsImage.add(goods, "/media/goods/main.jpg", true, 1),
                GoodsImage.add(goods, "/media/goods/gallery.jpg", false, 2)));
        contentBlocks.saveAllAndFlush(List.of(
                GoodsContentBlock.text(goods, "第一行\n第二行", 1),
                GoodsContentBlock.image(goods, "/media/description/a.jpg", 2),
                GoodsContentBlock.video(goods, "/media/description/a.mp4", 3)));

        flushAndClear();

        Goods loaded = goodsRepository.findById(goods.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo(goods.getName());
        assertThat(loaded.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isEqualTo(3);
        assertThat(loaded.getStatus()).isEqualTo(GoodsStatus.ON_SALE);
        assertThat(loaded.getOffShelfReason()).isNull();
        assertThat(loaded.getOffShelfAt()).isNull();
        assertThat(loaded.getCategory().getId()).isEqualTo(category.getId());

        assertThat(goodsImages.findByGoods_IdOrderBySortNoAsc(goods.getId())).hasSize(2);
        assertThat(goodsImages.findMainByGoodsId(goods.getId()).orElseThrow().isMain()).isTrue();

        List<GoodsContentBlock> blocks =
                contentBlocks.findByGoods_IdOrderBySortNoAsc(goods.getId());
        assertThat(blocks).extracting(GoodsContentBlock::getBlockType)
                .containsExactly(ContentBlockType.TEXT, ContentBlockType.IMAGE, ContentBlockType.VIDEO);
        assertThat(blocks.get(0).getTextContent()).isEqualTo("第一行\n第二行");
    }

    @Test
    @DisplayName("下架写入原因与时间，复售时两者一起清空")
    void offShelfAndBackOnSaleKeepReasonAndTimeInPair() {
        Goods goods = createGoods(createCategory());

        goods.offShelf(OffShelfReason.STOCK_ZERO);
        goodsRepository.saveAndFlush(goods);
        flushAndClear();

        Goods offShelf = goodsRepository.findById(goods.getId()).orElseThrow();
        assertThat(offShelf.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(offShelf.getOffShelfReason()).isEqualTo(OffShelfReason.STOCK_ZERO);
        assertThat(offShelf.getOffShelfAt()).isNotNull();
        assertThat(offShelf.isRestockable()).isTrue();

        offShelf.backOnSale();
        goodsRepository.saveAndFlush(offShelf);
        flushAndClear();

        Goods onSale = goodsRepository.findById(goods.getId()).orElseThrow();
        assertThat(onSale.getStatus()).isEqualTo(GoodsStatus.ON_SALE);
        assertThat(onSale.getOffShelfReason()).isNull();
        assertThat(onSale.getOffShelfAt()).isNull();
    }

    @Test
    @DisplayName("手动下架是终态：不能借复售方法回到在售")
    void manualOffShelfCannotBeRestocked() {
        Goods goods = createGoods(createCategory());
        goods.offShelf(OffShelfReason.MANUAL);
        goodsRepository.saveAndFlush(goods);

        assertThat(goods.isRestockable()).isFalse();
        assertThatThrownBy(goods::backOnSale).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DB CHECK 拒绝对在售商品写下架原因")
    void rejectsOnSaleGoodsCarryingOffShelfReason() {
        Goods goods = createGoods(createCategory());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update simpleshop_goods set off_shelf_reason = ?, off_shelf_at = ? where id = ?",
                "MANUAL", LocalDateTime.now(ZoneOffset.UTC), goods.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("DB CHECK 拒绝已下架但缺原因或缺下架时间的商品")
    void rejectsOffShelfGoodsWithoutReasonOrTime() {
        Goods goods = createGoods(createCategory());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update simpleshop_goods set status = 'OFF_SHELF', "
                        + "off_shelf_reason = null, off_shelf_at = null where id = ?",
                goods.getId()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update simpleshop_goods set status = 'OFF_SHELF', "
                        + "off_shelf_reason = 'MANUAL', off_shelf_at = null where id = ?",
                goods.getId()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update simpleshop_goods set status = 'FROZEN' where id = ?",
                goods.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("同一商品只能有一张主图")
    void rejectsSecondMainImage() {
        Goods goods = createGoods(createCategory());
        goodsImages.saveAndFlush(GoodsImage.add(goods, "/media/goods/one.jpg", true, 1));
        GoodsImage second = GoodsImage.add(goods, "/media/goods/two.jpg", true, 2);

        assertThatThrownBy(() -> goodsImages.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("同一商品的图片排序号唯一")
    void rejectsDuplicateImageSortNo() {
        Goods goods = createGoods(createCategory());
        goodsImages.saveAndFlush(GoodsImage.add(goods, "/media/goods/one.jpg", false, 1));
        GoodsImage duplicate = GoodsImage.add(goods, "/media/goods/two.jpg", false, 1);

        assertThatThrownBy(() -> goodsImages.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("描述内容块的 DB CHECK 拒绝 TEXT 块携带媒体 URL")
    void rejectsInvalidContentBlockShapeAtDatabase() {
        Goods goods = createGoods(createCategory());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into simpleshop_goods_content_block "
                        + "(id, fk_goods_id, block_type, sort_no, text_content, media_url, create_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                "GB-INVALID", goods.getId(), "TEXT", 99, "text", "/media/description/x.jpg",
                LocalDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(DataAccessException.class);
    }
}
