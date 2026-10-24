package com.simpleshop.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;

class CategoryRulesTest {

    @Test
    @DisplayName("类别名去首尾空白后保留内部空格")
    void trimsName() {
        assertThat(CategoryRules.name("  数码 产品  ")).isEqualTo("数码 产品");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "<数码>", "数码\n配件"})
    @DisplayName("空白、尖括号和控制字符被拒绝")
    void rejectsInvalidNames(String value) {
        assertThatThrownBy(() -> CategoryRules.name(value))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CATEGORY_NAME_INVALID));
    }

    @Test
    @DisplayName("超过 50 字符的类别名被拒绝")
    void rejectsTooLongName() {
        assertThatThrownBy(() -> CategoryRules.name("类".repeat(51)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CATEGORY_NAME_INVALID));
    }

    @Test
    @DisplayName("50 字符的类别名允许")
    void acceptsMaxLengthName() {
        assertThat(CategoryRules.name("类".repeat(50))).hasSize(50);
    }
}
