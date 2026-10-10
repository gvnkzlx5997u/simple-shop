package com.simpleshop.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.testing.CsvValues;

/**
 * 买家端字段校验的用例表测试（方案 §3.6 的 {@code intentions.name}／{@code intentions.tel} 两行）。
 *
 * <p>用例表：{@code /csv/intention-name-cases.csv}、{@code /csv/intention-tel-cases.csv}，
 * 列 {@code caseName,input,expectedPrompt,expectedStored}。取值写法（{@code NULL}／{@code ""}／
 * {@code {LF}}／{@code REPEAT(x:n)}）见 {@link CsvValues}。
 *
 * <h2>⚠️ {@code expectedPrompt} 列写 {@code OK} 表示「通过」</h2>
 * <p>刻意<b>不</b>用 {@code NULL} 当「通过」：{@code @CsvFileSource(nullValues = "NULL")} 会把该列的
 * {@code NULL} 也转成 {@code null}，于是「通过」与「列没写」无法区分。{@code OK} 是个明确的记号。
 *
 * <h2>⚠️ 为什么通过时还要核对 {@code expectedStored}</h2>
 * <p>只断言「没被拒」不够：{@code trim} 这类归一化<b>不改变</b>通过/拒绝，却改变<b>入库的值</b>。
 * 故通过分支一律额外比对归一化结果。
 */
class IntentionValidatorTest {

    /** 用例表中表示「校验通过」的记号。 */
    private static final String PASS = "OK";

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/intention-name-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("买家姓名校验用例表")
    void nameCases(String caseName, String input, String expectedPrompt, String expectedStored) {
        BuyerResult<String> result = IntentionValidator.normalizeName(CsvValues.expand(input));

        assertThat(result.rejectedPrompt())
                .as("用例「%s」的提示码", caseName)
                .isEqualTo(expected(expectedPrompt));
        if (PASS.equals(expectedPrompt)) {
            assertThat(result.orElseNull())
                    .as("用例「%s」应通过，且归一化为用例表给定的值", caseName)
                    .isEqualTo(CsvValues.expand(expectedStored));
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/intention-tel-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("买家电话校验用例表")
    void telCases(String caseName, String input, String expectedPrompt, String expectedStored) {
        BuyerResult<String> result = IntentionValidator.normalizeTel(CsvValues.expand(input));

        assertThat(result.rejectedPrompt())
                .as("用例「%s」的提示码", caseName)
                .isEqualTo(expected(expectedPrompt));
        if (PASS.equals(expectedPrompt)) {
            assertThat(result.orElseNull())
                    .as("用例「%s」应通过，且归一化为用例表给定的值", caseName)
                    .isEqualTo(CsvValues.expand(expectedStored));
        }
    }

    private static BuyerPrompt expected(String token) {
        return PASS.equals(token) ? null : BuyerPrompt.valueOf(token);
    }
}
