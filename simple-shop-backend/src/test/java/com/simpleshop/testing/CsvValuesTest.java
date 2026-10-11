package com.simpleshop.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CsvValues} 的自我校验。
 *
 * <h2>为什么这个类必须存在</h2>
 * <p>用例表里的「边界」信息（{@code REPEAT(商,50)}）是<b>数据</b>，而数据是可能写错的。
 * 如果展开规则本身有问题——例如 {@code REPEAT(😀,50)} 实际产生了 100 个 UTF-16 单元、
 * 50 个码点，而读取/展开过程把它们弄成了 100 个码点——那么
 * {@code GoodsValidatorTest} 里那条「恰好 50 码点」的用例就会<b>测到别的东西</b>，
 * 却依然显示绿色。
 *
 * <p>也就是说：<b>没有这个类，边界用例的绿是没有说服力的</b>。
 * 这里把「展开结果到底有多长、有几个码点」显式断言出来，
 * 使 {@code 50 码点 vs 100 UTF-16 单元} 这个区分成为<b>可核对的事实</b>。
 */
class CsvValuesTest {

    @Test
    @DisplayName("REPEAT 按【码点】重复，而非按 UTF-16 单元")
    void repeatCountsCodePoints() {
        // BMP 字符：1 码点 = 1 个 UTF-16 单元
        assertThat(CsvValues.expand("REPEAT(商:50)"))
                .hasSize(50)
                .matches(s -> s.codePointCount(0, s.length()) == 50);

        // 非 BMP 字符（emoji）：1 码点 = 2 个 UTF-16 单元。
        // 这正是 GoodsValidator 用 codePointCount 而不是 length 的原因——
        // 若按 length 判，「50 个 emoji」会被误判为 100 字符而被拒。
        String emojiFifty = CsvValues.expand("REPEAT(😀:50)");
        assertThat(emojiFifty)
                .as("50 个 emoji 在 UTF-16 下是 100 个单元")
                .hasSize(100);
        assertThat(emojiFifty.codePointCount(0, emojiFifty.length()))
                .as("但只有 50 个码点，即 50 个「字符」")
                .isEqualTo(50);

        // 码点写法与直接写字符必须等价
        assertThat(CsvValues.expand("REPEAT(U+1F600:50)"))
                .isEqualTo(emojiFifty);
    }

    @Test
    @DisplayName("REPEAT 可嵌在其它文本中（用于测 trim：外层空格必须保留）")
    void repeatExpandsInPlace() {
        assertThat(CsvValues.expand("  REPEAT(商:3)  "))
                .isEqualTo("  商商商  ")
                .hasSize(2 + 3 + 2);
        assertThat(CsvValues.expand("前缀REPEAT(a:2)后缀"))
                .isEqualTo("前缀aa后缀");
    }

    @Test
    @DisplayName("控制字符记号展开为真实字符")
    void expandsControlCharacterTokens() {
        assertThat(CsvValues.expand("{LF}")).isEqualTo("\n");
        assertThat(CsvValues.expand("{CR}")).isEqualTo("\r");
        assertThat(CsvValues.expand("{TAB}")).isEqualTo("\t");
        assertThat(CsvValues.expand("手工{LF}曲奇")).isEqualTo("手工\n曲奇");
        assertThat(CsvValues.expand("REPEAT(描:3){LF}")).isEqualTo("描描描\n");
    }

    @Test
    @DisplayName("null 与不含记号的普通文本原样返回")
    void passesThroughPlainValues() {
        assertThat(CsvValues.expand(null)).isNull();
        assertThat(CsvValues.expand("")).isEmpty();
        assertThat(CsvValues.expand("手工曲奇饼干")).isEqualTo("手工曲奇饼干");
        assertThat(CsvValues.expand("a<b")).isEqualTo("a<b");
    }

    @Test
    @DisplayName("⚠️ 写成逗号（REPEAT(商,50)）必须【报错】，绝不静默当普通文本")
    void commaSeparatorIsRejectedLoudly() {
        // 这是实现时真实踩到的坑：逗号是 CSV 的列分隔符，REPEAT(商,50) 会把整行拆错列。
        // 更危险的是「若容错放过」——用例会拿字面量 "REPEAT(商,50)" 去比对，
        // 校验器也原样接受，于是那条边界用例在【什么都没测】的情况下变绿。
        assertThatThrownBy(() -> CsvValues.expand("REPEAT(商,50)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未展开")
                .hasMessageContaining("冒号");
    }

    @Test
    @DisplayName("用例表写错时立刻失败，不静默当成普通文本")
    void failsFastOnMalformedTokens() {
        // 重复单元必须是【一个】字符：写两个字符说明用例表写错了
        assertThatThrownBy(() -> CsvValues.expand("REPEAT(ab:3)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单个");
        // 码点越界
        assertThatThrownBy(() -> CsvValues.expand("REPEAT(U+FFFFFF:3)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("码点非法");
    }
}
