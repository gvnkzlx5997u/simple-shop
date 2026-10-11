package com.simpleshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import com.simpleshop.testing.CsvValues;

/**
 * 口令码的<b>纯函数</b>行为（归一化与格式判定）——{@code PasscodeService} 的无容器部分。
 *
 * <p>生成部分（{@code generateUnique}，需要仓储）另见 {@code PasscodeGenerationTest}。
 * 这样切分是为了让「格式与归一化」这类高频规则用例<b>不必启动 Spring 上下文</b>就能跑。
 *
 * <p>用例表：{@code /csv/passcode-normalize-cases.csv}（{@code caseName,raw,expectedNormalized}）、
 * {@code /csv/passcode-wellformed-cases.csv}（{@code caseName,raw,expected}）。
 */
class PasscodeServiceTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        // 改默认区域是全局副作用，必须还原（同 UtcIso8601Test 处理默认时区的做法）
        Locale.setDefault(originalLocale);
    }

    // -------------------------------------------------------------------------
    // 归一化
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/passcode-normalize-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("口令码归一化用例表")
    void normalizeCases(String caseName, String raw, String expected) {
        assertThat(PasscodeService.normalize(CsvValues.expand(raw)))
                .as("用例「%s」", caseName)
                .isEqualTo(CsvValues.expand(expected));
    }

    // -------------------------------------------------------------------------
    // 格式判定
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/passcode-wellformed-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("口令码格式判定用例表（内部先归一化）")
    void wellFormedCases(String caseName, String raw, boolean expected) {
        assertThat(PasscodeService.isWellFormed(CsvValues.expand(raw)))
                .as("用例「%s」", caseName)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("长度恰为 12（缩短它会同时废掉 R12-03「不限错误次数」的取舍前提）")
    void lengthIsTwelve() {
        assertThat(PasscodeService.LENGTH).isEqualTo(12);

        // 12 位的边界：11 位与 13 位都不合法
        assertThat(PasscodeService.isWellFormed("ABCDEFGHIJK")).isFalse();
        assertThat(PasscodeService.isWellFormed("ABCDEFGHIJKL")).isTrue();
        assertThat(PasscodeService.isWellFormed("ABCDEFGHIJKLM")).isFalse();
    }

    // -------------------------------------------------------------------------
    // 默认区域无关性（本测试类存在的核心理由之一）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ 归一化必须与 JVM 默认区域无关——土耳其语下 'i' → 'İ' 的坑")
    void normalizeIsIndependentOfDefaultLocale() {
        Locale turkish = Locale.forLanguageTag("tr-TR");
        Locale.setDefault(turkish);

        // 先证明这个陷阱是真的：无参 toUpperCase() 用的是【默认区域】，
        // 在土耳其语下 "i" 会变成带点的 "İ"（U+0130）而不是 "I"。
        // 若 PasscodeService 当初写成 raw.toUpperCase()，下面这条断言就会失败。
        assertThat("i".toUpperCase(turkish))
                .as("证明陷阱真实存在：土耳其语区域下 i 的大写【不是】I")
                .isNotEqualTo("I");

        // 实现用的是 toUpperCase(Locale.ROOT)，故不受影响
        assertThat(PasscodeService.normalize("i")).isEqualTo("I");
        assertThat(PasscodeService.normalize("iabcdefghijk")).isEqualTo("IABCDEFGHIJK");
        assertThat(PasscodeService.isWellFormed("iabcdefghijk"))
                .as("全小写的 12 位口令码必须可用——否则土耳其语用户永远查不到自己的意向")
                .isTrue();
    }

    @Test
    @DisplayName("isWellFormed 内部先归一化：带空格/小写的原样输入也应判为合法")
    void wellFormedNormalizesBeforeChecking() {
        // §3.6 的原文是「^[A-Z0-9]{12}$（【归一化后校验】）」。
        // 若本方法不归一化，用户粘贴的 " abc123def456 " 会被判「格式不符」（B11-09），
        // 而查库时它又能命中——同一个输入在两处得到相反结论。
        assertThat(PasscodeService.isWellFormed("  abc123def456  ")).isTrue();
        assertThat(PasscodeService.isWellFormed("abc123def456")).isTrue();
        assertThat(PasscodeService.isWellFormed("ABC123DEF456")).isTrue();
    }
}
