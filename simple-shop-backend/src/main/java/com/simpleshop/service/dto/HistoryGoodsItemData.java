package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 历史商品列表的一行（{@code I11-15} 的 {@code items[]}）。依据：第 11 章 §11.5、方案 §4.6.2。
 *
 * <h2>⚠️ 只有 5 个字段——比历史商品的列少得多，这是契约要求，不是省略</h2>
 * <p>契约的 {@code P10-08}（历史商品列表）只展示「名称 + 发布时间 + 交易结束时间 + 结果」。
 * 特别地：
 * <ul>
 *   <li><b>不含 {@code trade_start}</b>（澄清 Q22：只记录、不展示）；</li>
 *   <li><b>不含 {@code description}／{@code pic_url}／{@code price}／{@code status}／{@code freeze_by}</b>
 *       ——那些是详情页（{@code I11-16}）的事。列表页一次拉 10 行，下发用不到的列只是无谓的传输。</li>
 * </ul>
 * <p>用 {@code record} 的组件清单把它钉住：想「顺手多带一列」在编译期就不可表达（§11.1）。
 *
 * @param id       历史商品 ID（沿用原商品 ID，前缀 {@code G}）
 * @param name     商品名称
 * @param createAt 发布时间（ISO 8601 UTC，继承原值）
 * @param tradeEnd 交易结束时间（= 归档时间；<b>列表的排序键</b>，倒序）
 * @param result   商品级结果：{@code sold}／{@code offline}
 */
public record HistoryGoodsItemData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("trade_end") String tradeEnd,
        @JsonProperty("result") String result) {

    /** 由历史商品实体构造（映射点）。逐字段显式赋值，理由见 {@code GoodsData} 的说明。 */
    public static HistoryGoodsItemData from(GoodsHistory goods) {
        return new HistoryGoodsItemData(
                goods.getId(),
                goods.getName(),
                UtcIso8601.of(goods.getCreateAt()),
                UtcIso8601.of(goods.getTradeEnd()),
                goods.getResult().name());
    }
}
