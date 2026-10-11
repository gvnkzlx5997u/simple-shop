package com.simpleshop.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.testing.CsvValues;

/**
 * 商品字段校验的用例表测试（方案 §3.6 的逐字段校验表）。
 *
 * <h2>用例表是 CSV 文件，不是方法体</h2>
 * <p>三张表分别对应三个字段：{@code /csv/goods-name-cases.csv}、
 * {@code goods-description-cases.csv}、{@code goods-price-cases.csv}。
 * 每一行是「用例名 → 输入 → 期望业务码 → 期望归一化结果」，加用例 = 加一行，
 * 不需要碰 Java 代码。
 *
 * <h2>取值写法（由 {@link CsvValues} 展开，见其类注释）</h2>
 * <p>{@code NULL} = {@code null}；{@code ""} = 空串；{@code {LF}}/{@code {CR}}/{@code {TAB}} = 控制字符；
 * {@code REPEAT(商,50)} = 该字符重复 50 次。
 *
 * <h2>⚠️ {@code expectedCode = 0} 表示「通过」，且必须同时核对归一化结果</h2>
 * <p>只断言「没抛异常」是不够的：{@code trim} 这类归一化不改变「通过/拒绝」，
 * 却改变<b>入库的值</b>。所以通过的分支一律额外比对 {@code expectedStored}。
 *
 * <h2>期望码 {@code 0} 与「业务码 {@code 0}」不是一回事</h2>
 * <p>这里用 {@code 0} 只是「本行期望成功」的标记（{@code ErrorCode.OK} 的码也是 {@code 0}，
 * 语义恰好一致）。失败行写的是真实业务码，故本测试<b>同时</b>钉住了
 * 「{@code 50002} 表示缺失、{@code 20007}/{@code 20008}/{@code 20010} 表示取值非法」这条分工。
 */
class GoodsValidatorTest {

    // =========================================================================
    // 商品名称
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/goods-name-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("名称校验用例表")
    void nameCases(String caseName, String input, int expectedCode, String expectedStored) {
        String value = CsvValues.expand(input);
        String stored = CsvValues.expand(expectedStored);

        if (expectedCode == 0) {
            assertThat(GoodsValidator.normalizeName(value))
                    .as("用例「%s」应通过，且归一化为用例表给定的值", caseName)
                    .isEqualTo(stored);
        } else {
            assertRejected(expectedCode, () -> GoodsValidator.normalizeName(value));
        }
    }

    // =========================================================================
    // 商品描述
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/goods-description-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("描述校验用例表")
    void descriptionCases(String caseName, String input, int expectedCode, String expectedStored) {
        String value = CsvValues.expand(input);
        String stored = CsvValues.expand(expectedStored);

        if (expectedCode == 0) {
            assertThat(GoodsValidator.normalizeDescription(value))
                    .as("用例「%s」应通过，且归一化为用例表给定的值", caseName)
                    .isEqualTo(stored);
        } else {
            assertRejected(expectedCode, () -> GoodsValidator.normalizeDescription(value));
        }
    }

    // =========================================================================
    // 商品价格
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/goods-price-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("价格校验用例表")
    void priceCases(String caseName, String input, int expectedCode, String expectedStored) {
        String value = CsvValues.expand(input);
        String stored = CsvValues.expand(expectedStored);

        if (expectedCode == 0) {
            assertThat(GoodsValidator.parsePrice(value))
                    .as("用例「%s」应通过，且归一化为用例表给定的值", caseName)
                    // ⚠️ 用 toPlainString 比对而不是 BigDecimal.equals：
                    //    equals 连 scale 一起比，且用例表里存的是字符串，这样比对更贴近契约（下发字符串）
                    .extracting(BigDecimal::toPlainString)
                    .isEqualTo(stored);
        } else {
            assertRejected(expectedCode, () -> GoodsValidator.parsePrice(value));
        }
    }

    @Test
    @DisplayName("价格：返回值恒为 scale=2（契约要求两位小数）")
    void priceIsAlwaysScaledToTwoDecimals() {
        assertThat(GoodsValidator.parsePrice("12").toPlainString()).isEqualTo("12.00");
        assertThat(GoodsValidator.parsePrice("12.3").toPlainString()).isEqualTo("12.30");
        assertThat(GoodsValidator.parsePrice("0.01").scale()).isEqualTo(2);
        assertThat(GoodsValidator.parsePrice("12").scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("价格：多条小数位用例被拒时【不得】静默四舍五入")
    void priceRejectsExcessScaleInsteadOfRounding() {
        // 1.999 若被静默舍入会变成 2.00（多收钱/少收钱都可能），契约要求「多余小数位直接拒绝」
        assertRejected(20010, () -> GoodsValidator.parsePrice("1.999"));
        assertRejected(20010, () -> GoodsValidator.parsePrice("1.995"));
        assertRejected(20010, () -> GoodsValidator.parsePrice("2.005"));
    }

    @Test
    @DisplayName("价格：超大科学计数法必须被【快速】拒绝（判定顺序不得让 setScale 展开大数）")
    void hugeScaleDoesNotBlowUp() {
        // 这一条同时是「判定顺序」的回归护栏：
        // 若实现写成「先 setScale(2) 再判区间」，1e100000000 会迫使 BigDecimal 展开成上亿位
        // 十进制数（内存/CPU 放大），本用例会超时或 OOM。
        long startNanos = System.nanoTime();

        assertRejected(20010, () -> GoodsValidator.parsePrice("1e100000000"));
        assertRejected(20010, () -> GoodsValidator.parsePrice("1e-100000000"));
        assertRejected(20010, () -> GoodsValidator.parsePrice("9e999999999"));

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        assertThat(elapsedMillis)
                .as("拒绝超大指数应当是常数时间；耗时 %d ms 说明数字被展开了", elapsedMillis)
                .isLessThan(1_000);
    }

    @Test
    @DisplayName("名称：含【裸】 < 与 > 一律拒绝，不做转义放行（NFR-20、§9.2.5）")
    void nameRejectsRawAngleBrackets() {
        assertRejected(20007, () -> GoodsValidator.normalizeName("<script>alert(1)</script>"));
        assertRejected(20007, () -> GoodsValidator.normalizeName("a>b"));
        assertRejected(20007, () -> GoodsValidator.normalizeName("<"));
    }

    @Test
    @DisplayName("名称：已转义的文本不含裸 <>，故【通过】——输出侧转义是另一层，两层不可互补")
    void alreadyEscapedTextIsAcceptedBecauseInputRuleOnlyRejectsRawBrackets() {
        // §3.6 的输入规则是「拒绝 < 与 >」，而 &lt;script&gt; 里一个裸尖括号都没有，
        // 它就是一段普通文本。这正说明「输入拒绝是第一层、输出转义是第二层，不可互补」：
        // 第一层管不住这种输入，所以渲染时【仍然】必须转义（C-15、NFR-20 ②，
        // 输出侧由前端/下一轮 JSP 负责）。若在这里「顺手」把它也拒掉，
        // 就等于用输入过滤去替代输出转义——那是被 NFR-20 明确否定过的做法。
        assertThat(GoodsValidator.normalizeName("&lt;script&gt;"))
                .isEqualTo("&lt;script&gt;");
    }

    @Test
    @DisplayName("名称：不允许换行（含 \\r，避免 \\r\\n 漏网）；描述允许换行")
    void nameRejectsCarriageReturnWhichCsvCannotExpress() {
        // 用例表里能可靠表达 {LF}，但把裸 CR 放进 CSV 会被 opencsv 按行分隔处理，
        // 故 \r 单独用代码写死（这是唯一一处「不入表」的输入，理由在此）。
        assertRejected(20007, () -> GoodsValidator.normalizeName("手工\r曲奇"));
        assertRejected(20007, () -> GoodsValidator.normalizeName("手工\r\n曲奇"));

        // 描述允许换行：CRLF 也应被接受（trim 只去掉首尾）
        assertThat(GoodsValidator.normalizeDescription("第一行\r\n第二行"))
                .isEqualTo("第一行\r\n第二行");
    }

    @Test
    @DisplayName("描述：空与 null 都收敛为 null（库里不出现空串描述）")
    void blankDescriptionBecomesNull() {
        assertThat(GoodsValidator.normalizeDescription(null)).isNull();
        assertThat(GoodsValidator.normalizeDescription("")).isNull();
        assertThat(GoodsValidator.normalizeDescription("   ")).isNull();
        assertThat(GoodsValidator.normalizeDescription("\n\n")).isNull();
        assertThat(GoodsValidator.normalizeDescription(" 有内容 ")).isEqualTo("有内容");
    }

    @Test
    @DisplayName("名称：边界按【码点】计，50 个 emoji 合法、51 个非法（用例表之外的显式复述）")
    void nameLengthBoundaryUsesCodePoints() {
        String fiftyEmoji = CsvValues.expand("REPEAT(😀:50)");
        String fiftyOneEmoji = CsvValues.expand("REPEAT(😀:51)");

        assertThat(fiftyEmoji).hasSize(100);   // UTF-16 单元数
        assertThat(GoodsValidator.normalizeName(fiftyEmoji)).isEqualTo(fiftyEmoji);
        assertRejected(20007, () -> GoodsValidator.normalizeName(fiftyOneEmoji));
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    /**
     * 断言动作被拒且业务码正确。
     *
     * <p>先断言异常<b>类型</b>再取码：若实现抛的是 {@code NullPointerException} 或
     * {@code NumberFormatException}（而不是 {@code BusinessException}），
     * 那么「码对不上」这种信息会掩盖真正的问题——这里要让失败信息直接指出「抛错了类型」。
     */
    private static void assertRejected(int expectedCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown)
                .as("应当以业务异常拒绝（期望业务码 %d）", expectedCode)
                .isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().code())
                .as("业务码")
                .isEqualTo(expectedCode);
    }
}
