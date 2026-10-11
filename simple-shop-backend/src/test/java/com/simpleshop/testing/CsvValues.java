package com.simpleshop.testing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSV 用例表里「值」的展开规则（供各参数化测试共用）。
 *
 * <h2>为什么用例表需要一个展开约定</h2>
 * <p>下面这些东西<b>没法可靠地直接写进 CSV 单元格</b>：
 * <ul>
 *   <li><b>换行／制表符</b>：要么在 CSV 里放一个真实的多行字段（对读者极不友好、
 *       且容易被编辑器的自动换行规范化掉），要么放转义序列——后者一眼能看懂；</li>
 *   <li><b>长度边界</b>：{@code 恰好 50 字符} 与 {@code 51 字符} 如果写成字面量，
 *       读者<b>无法用眼睛分辨</b>。写成 {@code REPEAT(商,50)} 后，
 *       「50」是一个显式、可核对的数据——这正是用例表该有的样子。
 *       （也顺带避免了「手数错字数导致用例其实没测到边界」这种假绿。）</li>
 * </ul>
 *
 * <h2>约定（各用例表中通用）</h2>
 * <table border="1">
 *   <caption>值的写法</caption>
 *   <tr><th>写法</th><th>含义</th></tr>
 *   <tr><td>{@code NULL}</td><td>{@code null}（由 {@code @CsvFileSource(nullValues = "NULL")} 直接转换，不经本类）</td></tr>
 *   <tr><td>{@code ""}</td><td>空串（CSV 的引号空字段，长度 0）</td></tr>
 *   <tr><td>{@code {LF}}</td><td>换行 {@code \n}（U+000A）</td></tr>
 *   <tr><td>{@code {CR}}</td><td>回车 {@code \r}（U+000D）</td></tr>
 *   <tr><td>{@code {TAB}}</td><td>制表符 {@code \t}（U+0009）</td></tr>
 *   <tr><td>{@code REPEAT(商:50)}</td><td>字符 {@code 商} 重复 50 次</td></tr>
 *   <tr><td>{@code REPEAT(U+1F600:50)}</td><td>按码点重复（用码点写法的场合：该字符不便直接键入）</td></tr>
 * </table>
 * <p>展开是<b>逐个子串替换</b>，因此 {@code "  REPEAT(商:48)  "} 也会正确展开
 * （外层的空格保留，用于测 {@code trim}）。
 *
 * <h2>⚠️ 分隔符是【冒号】而不是逗号——因为逗号是 CSV 的列分隔符</h2>
 * <p>这不是审美选择。若写成 {@code REPEAT(商,50)}，那个逗号会被 CSV 解析器当成列边界，
 * 整行<b>向右错位</b>：{@code 50)} 会掉进「期望业务码」列，报出
 * {@code Failed to convert String "50)" to type int} 这种与被测逻辑毫无关系的错误。
 * 这是实现本类时真实踩到的坑，故改用冒号，并要求所有 {@code REPEAT} 一律不引号——
 * 行列对齐用肉眼就能核对。
 *
 * <h2>⚠️ 未展开的 {@code REPEAT(} 一律抛异常（防「假绿」）</h2>
 * <p>若容错地把不认识的记号当普通文本放过去，用例就会拿
 * {@code "REPEAT(商,50)"} 这 14 个字符去比对，而校验器也原样接受它 ——
 * <b>于是「恰好 50 字符」这条边界用例在什么都没测的情况下变绿</b>。
 * 因此 {@link #expand} 在展开后若仍发现 {@code REPEAT(}，一律抛
 * {@link IllegalArgumentException}。任何写法错误都必须是响亮的。
 */
public final class CsvValues {

    /**
     * {@code REPEAT(<字符或码点>:<次数>)}。
     *
     * <p>字符部分取「到第一个冒号为止」，故它本身不能是冒号或括号——用例表里也不需要。
     */
    private static final Pattern REPEAT = Pattern.compile("REPEAT\\(([^:()]+):(\\d+)\\)");

    /** 展开后用于自检「有没有漏网的记号」。 */
    private static final String REPEAT_MARKER = "REPEAT(";

    private static final Pattern CODEPOINT = Pattern.compile("^U\\+([0-9A-Fa-f]{4,6})$");

    private CsvValues() {
    }

    /**
     * 按类注释的约定展开一个单元格取值。
     *
     * @param raw 从 CSV 读到的原始字符串；{@code null} 原样返回
     * @return 展开后的值
     * @throws IllegalArgumentException 记号写法非法（用例表写错时应立刻失败，而不是静默当成普通文本）
     */
    public static String expand(String raw) {
        if (raw == null) {
            return null;
        }
        String withRepeats = expandRepeats(raw);
        if (withRepeats.contains(REPEAT_MARKER)) {
            throw new IllegalArgumentException(
                    "用例表里有未展开的 " + REPEAT_MARKER + " 记号。约定写法是 REPEAT(字符:次数)，"
                            + "用【冒号】而不是逗号（逗号是 CSV 的列分隔符）。原文: " + raw);
        }
        return withRepeats
                .replace("{LF}", "\n")
                .replace("{CR}", "\r")
                .replace("{TAB}", "\t");
    }

    private static String expandRepeats(String raw) {
        Matcher matcher = REPEAT.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String unit = resolveUnit(matcher.group(1));
            int times = Integer.parseInt(matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(unit.repeat(times)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 把 {@code REPEAT} 的第一个参数解析成「一个重复单元」（可能是代理对，故返回 String）。 */
    private static String resolveUnit(String unitToken) {
        Matcher codePoint = CODEPOINT.matcher(unitToken);
        if (codePoint.matches()) {
            int codePointValue = Integer.parseInt(codePoint.group(1), 16);
            if (!Character.isValidCodePoint(codePointValue)) {
                throw new IllegalArgumentException("用例表中的码点非法: " + unitToken);
            }
            return new String(Character.toChars(codePointValue));
        }
        if (unitToken.codePointCount(0, unitToken.length()) != 1) {
            throw new IllegalArgumentException(
                    "REPEAT 的第一个参数必须是【单个】字符或 U+XXXX 码点，实际为: " + unitToken);
        }
        return unitToken;
    }
}
