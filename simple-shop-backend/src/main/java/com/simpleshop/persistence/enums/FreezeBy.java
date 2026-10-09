package com.simpleshop.persistence.enums;

/**
 * 冻结来源字典（设计说明书 §5.2、§9.9.2）。
 *
 * <p>用于 {@code simpleshop_goods.freeze_by}；{@code status = frozen} 时必填（{@code BR-07}），
 * 该「条件必填」由应用层校验（数据库侧可空，§10.2）。
 *
 * <p><b>⚠️ 常量名即库中代码</b>，取值域不得增删（§5.1）。
 */
public enum FreezeBy {

    /** 手动冻结（卖家操作）。 */
    manual("手动冻结"),

    /** 交易冻结（进入交易时自动置冻结）。 */
    trade("交易冻结");

    private final String label;

    FreezeBy(String label) {
        this.label = label;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }
}
