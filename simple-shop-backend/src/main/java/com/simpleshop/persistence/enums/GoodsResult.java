package com.simpleshop.persistence.enums;

/**
 * 商品交易结果字典（设计说明书 §5.2、§9.9.5）。
 *
 * <p>用于 {@code simpleshop_goods.result}、{@code simpleshop_goods_history.result}。
 *
 * <p><b>⚠️ {@code simpleshop_goods.result} 在本表恒为 NULL</b>（{@code DEC-DB-11}、§10.6）：
 * 该列只在归档时随整行复制到 {@code simpleshop_goods_history}。实体保留了该属性的映射
 * 以对齐字典，但<b>应用层不得写入</b>当前商品表的该列。
 *
 * <p><b>⚠️ 常量名即库中代码</b>，取值域不得增删（§5.1）。
 */
public enum GoodsResult {

    /** 成功卖出。 */
    sold("成功卖出"),

    /** 手动下架。 */
    offline("手动下架");

    private final String label;

    GoodsResult(String label) {
        this.label = label;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }
}
