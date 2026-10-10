package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.TradeHistory;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 一条交易流水（{@code I11-16} 的 {@code trades[]} 元素，满足澄清 Q15／{@code BR-22}）。
 *
 * <p>依据：第 11 章 §11.5、方案 §4.6.2 的 {@code TradeData}。
 *
 * <h2>⚠️ 这个 DTO 存在的理由：只给最终结果不满足业务方要求</h2>
 * <p>{@code BR-22}／澄清 Q15 要求「某意向经历过的**每一次**失败都能查到」。
 * 只返回意向的最终 {@code status}／{@code fail_type} 会把「失败过两次、最后成交」的历史压扁成
 * 「succeeded」，买家侧与卖家侧的追溯都断掉。故 {@code trades[]} 必须逐条返回。
 *
 * <h2>⚠️⚠️ 数组内的时间先后在同一秒内<b>没有保证</b>（S7 实测，见 §8.12 O-1）</h2>
 * <p>{@code trade_start} 是秒精度 {@code datetime}，同一秒内产生的多条流水其 {@code trade_start}
 * 完全相同，而仓储按它排序——对这些行<b>先后不确定</b>。
 * 故：<b>本数组的语义是「这些失败都发生过」，不是「按时间严格排序的序列」</b>；
 * 界面上不得声称严格时序，用例也不得断言顺序（会偶发失败）。
 *
 * @param tradeStart 该次「进入交易」的时间（ISO 8601 UTC）
 * @param tradeEnd   该次「标记交易结果」的时间（ISO 8601 UTC）
 * @param result     流水级结果：{@code sold}／{@code failed}（<b>注意与商品级结果的差异</b>）
 * @param failType   失败类型代码，可 {@code null}（{@code result = sold} 时必为 {@code null}）
 * @param failReason 失败备注，可 {@code null}
 */
public record TradeData(
        @JsonProperty("trade_start") String tradeStart,
        @JsonProperty("trade_end") String tradeEnd,
        @JsonProperty("result") String result,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("fail_reason") String failReason) {

    /** 由流水实体构造（映射点）。 */
    public static TradeData from(TradeHistory trade) {
        return new TradeData(
                UtcIso8601.of(trade.getTradeStart()),
                UtcIso8601.of(trade.getTradeEnd()),
                trade.getResult().name(),
                trade.getFailType() == null ? null : trade.getFailType().name(),
                trade.getFailReason());
    }
}
