package com.simpleshop.service.dto;

/**
 * 口令码查询的三态结果（{@code FR-15}／{@code FR-22}、{@code BR-28}、{@code DEC-11}）。
 *
 * <p>方案 §3.3.5 的原始定义：{@code sealed interface PasscodeQueryResult}，
 * 三个 record：{@code NotFound} / {@code Expired} / {@code Active(rank, trading)}。
 *
 * <h2>⚠️ 本类存在的核心理由：把 {@code BR-29} 做进<b>类型</b></h2>
 * <p>{@code BR-29}／{@code NFR-13} 要求「口令码失效时<b>不说明原因、不返回买家信息、
 * 不透露交易结果</b>」。若用一个字段宽松的 DTO（比如带 {@code status}、{@code failType}、
 * {@code name}、{@code tel}）返回，那么「不泄漏」就只剩一条纪律：
 * <b>谁哪天顺手把状态填上，泄漏就发生了，而且不报错</b>。
 *
 * <p>故本实现刻意让泄漏<b>无法表达</b>：
 * <ul>
 *   <li>{@link NotFound} 与 {@link Expired} 是<b>零字段</b>记录——
 *       想「顺便带出失败原因」都没有地方放。方案 §3.3.5 要求它们
 *       「只带一个提示类型字段、不带任何买家或商品字段」；本实现连那一个字段都省了，
 *       改由 {@link #prompt()} 从<b>类型本身</b>推导——比带字段更严。</li>
 *   <li>只有 {@link Active} 带数据，且只有 {@code rank}（位次）——买家自己的信息，
 *       不涉及其它买家。</li>
 * </ul>
 *
 * <h2>⚠️ 三态的判定式只用 {@code status.isTokenValid()}，<b>绝不掺入商品状态</b></h2>
 * <p>{@code BR-26}、{@code INV-07}：商品在 {@code on_sale ⇄ frozen} 之间变化时，
 * <b>同一口令码的返回结果必须不变</b>。因此本类（以及产生它的查询）不看
 * {@code goods.status}、也不看在售/下架——只看意向自身的状态。
 * 这一点已登记为 {@code INV-07} 的用例。
 *
 * @see BuyerPrompt
 */
public sealed interface PasscodeQueryResult {

    /** 口令码不存在（错误口令）→ {@code B11-04}／{@code M10-05}。 */
    record NotFound() implements PasscodeQueryResult {
    }

    /**
     * 口令码存在，但意向已处于终态（{@code succeeded}／{@code failed}／{@code revoked}）
     * → {@code B11-05}／{@code M10-06}。
     *
     * <p><b>零字段</b>：不得携带 {@code status}／{@code failType}／买家信息／商品信息，
     * 见类注释的 {@code BR-29}。
     */
    record Expired() implements PasscodeQueryResult {
    }

    /**
     * 口令码有效。
     *
     * @param rank    {@code queued} 时的位次（<b>从 1 起</b>）；{@code trading} 时<b>必须为
     *                {@code null}</b>——{@code trading} 不占位次（§9.5.3、{@code M10-08}）
     * @param trading 是否处于交易中
     */
    record Active(Integer rank, boolean trading) implements PasscodeQueryResult {

        /**
         * 紧凑构造器：把「{@code trading} ⟺ 无位次」这条规则变成<b>构造期断言</b>。
         *
         * <p>若不校验，调用方可能拿到 {@code Active(3, true)} 这种「既在交易中又排第 3」的
         * 自相矛盾对象，渲染出「您已进入交易（当前排第 3 位）」。
         * {@code AC-11} 明确要求可查位次/已进入交易两者互斥，故这里直接不让它存在。
         */
        public Active {
            if (trading != (rank == null)) {
                throw new IllegalArgumentException(
                        "trading 与 rank 必须互斥：trading=true 时 rank 必须为 null，"
                                + "trading=false 时 rank 必须有值。实际 rank=" + rank + ", trading=" + trading);
            }
        }
    }

    /**
     * 本结果对应的界面提示码。
     *
     * <p>与 {@link BuyerResult#rejectedPrompt()} 不同，本方法是<b>全函数</b>——
     * 三态<b>各有</b>明确的提示码，不存在「没有提示」的情形，故不返回 {@code null}。
     *
     * <p>注意 {@link BuyerPrompt#QUEUED_RANK} 的文案含占位符 {@code N}：
     * 渲染方需用 {@code ((Active) result).rank()} 替换（{@code M10-07}）。
     *
     * @return 该状态对应的提示码
     */
    default BuyerPrompt prompt() {
        return switch (this) {
            case NotFound ignored -> BuyerPrompt.PASSCODE_NOT_FOUND;
            case Expired ignored -> BuyerPrompt.PASSCODE_EXPIRED;
            case Active active -> active.trading() ? BuyerPrompt.ENTERED_TRADE : BuyerPrompt.QUEUED_RANK;
        };
    }
}
