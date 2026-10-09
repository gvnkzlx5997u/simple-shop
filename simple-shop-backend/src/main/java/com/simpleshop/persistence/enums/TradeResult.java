package com.simpleshop.persistence.enums;

/**
 * 交易流水结果字典（设计说明书 §5.2、§9.9.6）。
 *
 * <p>用于 {@code simpleshop_trade_history.result}。这是<b>流水级</b>结果
 * （「该次交易成功／失败」），与 {@link GoodsResult}（商品级）不是同一口径。
 *
 * <p><b>⚠️ 常量名即库中代码</b>，取值域不得增删（§5.1）。
 */
public enum TradeResult {

    /** 该次交易成功。 */
    sold("该次交易成功"),

    /** 该次交易失败。 */
    failed("该次交易失败");

    private final String label;

    TradeResult(String label) {
        this.label = label;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }
}
