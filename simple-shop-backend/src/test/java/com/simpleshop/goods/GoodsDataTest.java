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
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.enums.GoodsHistoryResult;
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
        assertThat(loaded.getStock()).isEqualTo(3);
        assertThat(loaded.getStatus()).isEqualTo(GoodsStatus.ON_SALE);
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

    @Test
    @DisplayName("历史商品保存描述与图片 JSON 快照")
    void archivesGoodsWithJsonSnapshots() {
        Goods goods = createGoods(createCategory());
        GoodsHistory history = GoodsHistory.archive(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                0,
                "一级类别",
                "二级类别",
                "[{\"type\":\"text\",\"text\":\"hello\"}]",
                "[{\"url\":\"/media/goods/main.jpg\",\"is_main\":true,\"sort_no\":1}]",
                goods.getCreateAt());

        goodsHistories.saveAndFlush(history);
        flushAndClear();

        GoodsHistory loaded = goodsHistories.findById(goods.getId()).orElseThrow();
        assertThat(loaded.getResult()).isEqualTo(GoodsHistoryResult.SOLD_OUT);
        assertThat(loaded.getDescriptionSnapshot()).contains("\"text\"").contains("hello");
        assertThat(loaded.getImagesSnapshot()).contains("\"is_main\"");
        assertThat(loaded.getArchivedAt()).isNotNull();
    }
}
