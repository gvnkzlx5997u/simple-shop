package com.simpleshop.goods.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;

class GoodsRulesTest {

    @Test
    @DisplayName("商品名去首尾空白并保留内部空格")
    void trimsName() {
        assertThat(GoodsRules.name("  手工 商品  ")).isEqualTo("手工 商品");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "<script>", "商品\n名称"})
    @DisplayName("商品名拒绝空白、尖括号和控制字符")
    void rejectsInvalidNames(String value) {
        assertThatThrownBy(() -> GoodsRules.name(value))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOODS_NAME_INVALID));
    }

    @Test
    @DisplayName("商品名最多 50 字符")
    void rejectsTooLongName() {
        assertThatThrownBy(() -> GoodsRules.name("商".repeat(51)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOODS_NAME_INVALID));
    }

    @Test
    @DisplayName("价格为正数、最多两位小数且不超过上限")
    void acceptsValidPrice() {
        assertThat(GoodsRules.price(new BigDecimal("99.9"))).isEqualByComparingTo("99.90");
        assertThat(GoodsRules.price(new BigDecimal("999999.99"))).isEqualByComparingTo("999999.99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.234", "1000000.00"})
    @DisplayName("非法价格被拒绝")
    void rejectsInvalidPrice(String value) {
        assertThatThrownBy(() -> GoodsRules.price(new BigDecimal(value)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRICE_INVALID));
    }

    @Test
    @DisplayName("发布库存必须至少为 1，调整库存必须非负")
    void validatesStock() {
        assertThat(GoodsRules.publishStock(1)).isEqualTo(1);
        assertThat(GoodsRules.adjustStock(0)).isZero();
        assertThatThrownBy(() -> GoodsRules.publishStock(0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STOCK_INVALID));
        assertThatThrownBy(() -> GoodsRules.adjustStock(-1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STOCK_INVALID));
    }

    @Test
    @DisplayName("描述文本允许换行，拒绝尖括号和超长内容")
    void validatesDescriptionText() {
        assertThat(GoodsRules.descriptionText("第一行\n第二行")).isEqualTo("第一行\n第二行");
        assertThat(GoodsRules.descriptionText("字".repeat(1000))).hasSize(1000);
        assertThatThrownBy(() -> GoodsRules.descriptionText("字".repeat(1001)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOODS_DESCRIPTION_INVALID));
        assertThatThrownBy(() -> GoodsRules.descriptionText("<b>文本</b>"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOODS_DESCRIPTION_INVALID));
    }
}
