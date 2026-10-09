package com.simpleshop.persistence.enums;

/**
 * 商品状态字典（设计说明书 §5.2、§9.9.1）。
 *
 * <p>用于 {@code simpleshop_goods.status}、{@code simpleshop_goods_history.status}。
 *
 * <p><b>⚠️ 常量名即库中代码</b>：本枚举以小写下划线形式命名，配合
 * {@code @Enumerated(EnumType.STRING)} 使存入库中的字符串与字典**逐字一致**。
 * 若写成驼峰（如 {@code onSale}），Spring Boot 的命名策略只作用于表名与列名、
 * **不影响枚举值**，落库就会变成 {@code "onSale"}，与字典不符（§5.1）。
 *
 * <p>取值域<b>不得增删</b>，须与 §5.2 逐字一致。
 */
public enum GoodsStatus {

    /** 在售。 */
    on_sale("在售", false),

    /** 已冻结。 */
    frozen("已冻结", false),

    /** 已下架。<b>仅出现在历史商品表</b>，当前商品表不落该值（§4.1.1、{@code 9-H}）。 */
    off_sale("已下架", true);

    private final String label;
    private final boolean terminal;

    GoodsStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }

    /** 是否终态。口径见 §5.3：{@code off_sale} = true，其余 false。 */
    public boolean isTerminal() {
        return terminal;
    }
}
