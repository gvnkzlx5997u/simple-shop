package com.simpleshop.persistence.enums;

/**
 * 购买意向状态字典（设计说明书 §5.2、§9.9.3）。
 *
 * <p>用于 {@code simpleshop_intentions.status}、{@code simpleshop_intentions_history.status}。
 *
 * <p><b>⚠️ 常量名即库中代码</b>，取值域不得增删（§5.1）。
 *
 * <p><b>关于 {@link #isTokenValid()}</b>：按 §5.3 与 {@code BR-26}／{@code INV-07}，
 * 其判定式为 <b>{@code tokenValid = !terminal}</b>，且这是<b>唯一</b>判定式。
 * <b>不得</b>把「商品是否下架」混入该判定（{@code BR-26}、{@code INV-07}）。
 */
public enum IntentionStatus {

    /** 排队中。非终态；买家可撤销；口令码有效。 */
    queued("排队中", false, true),

    /** 交易中。非终态；<b>不可撤销</b>（进入交易后买家不能撤销，{@code BR-19}）；口令码有效。 */
    trading("交易中", false, false),

    /** 交易成功。终态；口令码失效。 */
    succeeded("交易成功", true, false),

    /** 交易失败。终态；口令码失效。 */
    failed("交易失败", true, false),

    /** 已撤销。终态；口令码失效。 */
    revoked("已撤销", true, false);

    private final String label;
    private final boolean terminal;
    private final boolean revocable;

    IntentionStatus(String label, boolean terminal, boolean revocable) {
        this.label = label;
        this.terminal = terminal;
        this.revocable = revocable;
    }

    /** 中文名，<b>仅供界面与文档</b>，不落库（{@code 9-Q}）。 */
    public String getLabel() {
        return label;
    }

    /**
     * 是否终态。口径见 §5.3：{@code succeeded}／{@code failed}／{@code revoked} = true。
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 口令码对该状态是否有效。
     *
     * <p>口径（§5.3、{@code BR-26}、{@code INV-07}）：<b>{@code tokenValid = !isTerminal()}</b>。
     * 这是唯一判定式，<b>不得</b>掺入商品下架状态。
     */
    public boolean isTokenValid() {
        return !terminal;
    }

    /**
     * 买家可否撤销该意向。口径见 §5.3、{@code BR-19}：仅 {@code queued} = true
     * （{@code trading} 已进入交易，不可撤销）。
     */
    public boolean isRevocable() {
        return revocable;
    }
}
