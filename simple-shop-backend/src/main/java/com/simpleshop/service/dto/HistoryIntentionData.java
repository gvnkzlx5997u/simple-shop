package com.simpleshop.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.IntentionHistory;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 历史意向的一行（{@code I11-16} 的 {@code intentions[]}）。依据：第 11 章 §11.5、方案 §4.6.2。
 *
 * <h2>⚠️ 绝不含 {@code queue_order} 与 {@code token}（{@code 11-I}）</h2>
 * <p>{@code 11-I} 明确历史详情「均不返回」这两列。本记录<b>没有</b>这两个组件，
 * 因此「顺手带上」在编译期不可表达。
 * <p>更彻底的一层：连<b>实体</b> {@code IntentionHistory} 都根本没有这两个属性
 * （{@code DEC-DB-05}、{@code 9-S} 已从历史意向表删除该两列）——所以这不是「记得别加」，
 * 而是<b>数据层根本没有</b>。理由：{@code queue_order} 是过程性辅助数据（队列已清空、位次无意义）；
 * {@code token} 在归档时必已失效（归档前置要求全部终态，而终态即口令码失效），
 * 且 {@code FR-24}／{@code NFR-16} 明确「对终态意向不返回可用口令码」。
 *
 * <h2>⚠️ 排序键是 {@code create_at}，且同样是秒精度（{@code 11-H}）</h2>
 * <p>历史名单按「意向提交时间」升序（{@code 11-H}）——因为 {@code queue_order} 已不存在，
 * 而 {@code create_at} 才是「谁先提交」的表达。⚠️ 与 {@code trades[]} 同一类注意：
 * {@code create_at} 亦是秒精度，<b>同一秒内提交的意向之间先后不确定</b>
 * （真实场景几乎不可能，但造数型用例会立刻遇到——详见 §8.12 O-1）。
 *
 * @param id         意向 ID（沿用原值，前缀 {@code I}）
 * @param name       买家姓名
 * @param tel        联系电话
 * @param createAt   原始提交时间（ISO 8601 UTC，继承原值；<b>亦为排序键</b>）
 * @param status     终态代码：{@code succeeded}／{@code failed}／{@code revoked}
 * @param failType   失败类型代码，可 {@code null}
 * @param failReason 失败备注，可 {@code null}（<b>买家不可见</b>，但历史详情是卖家页面）
 * @param tradeCount 交易次数 = {@code trades.size()}（业务上恒等，见下方说明）
 * @param trades     该意向经历过的每一次交易（澄清 Q15、{@code BR-22}）
 */
public record HistoryIntentionData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("tel") String tel,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("status") String status,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("fail_reason") String failReason,
        @JsonProperty("trade_count") int tradeCount,
        @JsonProperty("trades") List<TradeData> trades) {

    /**
     * 由历史意向实体与其流水构造（映射点）。
     *
     * <h2>⚠️ {@code tradeCount} 直接取 {@code trades.size()}，<b>不</b>再单独查一次计数</h2>
     * <p>方案 §3.3.4 第 5 步明确：两者理论恒等，取长度可以<b>从根上避免</b>
     * 「计数说 2、数组里只有 1 条」这类不一致；也少一次 SQL。
     * 仓储虽然有批量计数方法（{@code countGroupedByIntentionIdIn}），但那需要**额外一次查询**
     * 才能拿到同样的信息，本实现<b>默认不用</b>它——若日后为性能改用批量查询，
     * 必须同时保留「{@code tradeCount == trades.size()}」的断言（§8.3 第 13 条）。
     */
    public static HistoryIntentionData of(IntentionHistory intention, List<TradeData> trades) {
        return new HistoryIntentionData(
                intention.getId(),
                intention.getName(),
                intention.getTel(),
                UtcIso8601.of(intention.getCreateAt()),
                intention.getStatus().name(),
                intention.getFailType() == null ? null : intention.getFailType().name(),
                intention.getFailReason(),
                trades.size(),
                trades);
    }
}
