package com.simpleshop.persistence.enums;

/**
 * 失败类型字典（设计说明书 §5.2、§9.9.4）。
 *
 * <p>用于 {@code simpleshop_intentions.fail_type}、{@code simpleshop_intentions_history.fail_type}、
 * {@code simpleshop_trade_history.fail_type}。
 *
 * <p><b>⚠️ 常量名即库中代码</b>，取值域不得增删（§5.1）。
 *
 * <p><b>买家不可见</b>（{@link #isBuyerVisible()} 恒为 false，{@code DEC-24}、{@code BR-29}）。
 * 注意：{@code fail_reason}（失败备注）属卖家手填、买家亦不可见，但本枚举只承载 {@code fail_type}。
 */
public enum FailType {

    /** 商品已售出（系统自动）。 */
    sold_out("商品已售出", true),

    /** 买家撤销（系统自动）。 */
    revoked("买家撤销", true),

    /** 卖家作废（卖家手动）。 */
    voided("卖家作废", false),

    /** 重新排队（卖家手动）。 */
    requeued("重新排队", false);

    private final String label;
    private final boolean auto;

    FailType(String label, boolean auto) {
        this.label = label;
        this.auto = auto;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }

    /**
     * 是否由系统自动产生。口径见 §5.3：
     * {@code sold_out}／{@code revoked} = true；{@code voided}／{@code requeued} = false。
     */
    public boolean isAuto() {
        return auto;
    }

    /**
     * 买家是否可见。<b>恒为 false</b>（{@code DEC-24}、{@code BR-29}）。
     *
     * <p>以方法形式提供而非静态常量，使调用处（{@code failType.isBuyerVisible()}）
     * 与其它枚举的读取方式一致，Service 层无需特判。
     */
    public boolean isBuyerVisible() {
        return false;
    }
}
