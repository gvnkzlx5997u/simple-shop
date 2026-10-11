package com.simpleshop.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 历史商品详情（{@code I11-16} 的 {@code data}）。依据：第 11 章 §11.5、方案 §4.6.2、澄清 Q15。
 *
 * <h2>字段＝历史商品的 9 列 + 意向名单</h2>
 * <p>商品部分：{@code id}／{@code name}／{@code description}／{@code pic_url}／{@code price}／
 * {@code status}（<b>恒 {@code off_sale}</b>）／{@code create_at}／{@code trade_end}／{@code result}。
 * <p>⚠️ <b>不含 {@code trade_start}</b>（澄清 Q22：只记录、不展示）——注意历史<b>实体</b>上是有该列的，
 * 只是详情页不下发它。这与 {@code freeze_by}／{@code update_at} 不同：那两个是契约从未登记的列
 * （历史实体上存在，但 §11.5 的字段表里没有），同样不下发。
 *
 * <h2>⚠️ {@code status} 恒为 {@code off_sale}</h2>
 * <p>归档时恒写 {@code off_sale}（{@code 9-S} 的归档例外①），故详情页的下拉框永远是「已下架」。
 * 字段仍然下发，因为前端要按它渲染状态标签，而不是自己假设。
 *
 * @param id          历史商品 ID（沿用原值，前缀 {@code G}）
 * @param name        商品名称
 * @param description 商品描述，可 {@code null}
 * @param picUrl      图片相对路径，可 {@code null}（⚠️ 归档<b>不搬迁图片文件</b>，路径照旧可用）
 * @param price       价格（JSON <b>字符串</b>，两位小数）
 * @param status      状态代码，恒 {@code off_sale}
 * @param createAt    发布时间（ISO 8601 UTC，继承原值）
 * @param tradeEnd    交易结束时间（= 归档时间）
 * @param result      商品级结果：{@code sold}／{@code offline}
 * @param intentions  意向名单，按 {@code create_at} 升序（{@code 11-H}）
 */
public record HistoryGoodsDetailData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("pic_url") String picUrl,
        @JsonProperty("price") String price,
        @JsonProperty("status") String status,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("trade_end") String tradeEnd,
        @JsonProperty("result") String result,
        @JsonProperty("intentions") List<HistoryIntentionData> intentions) {

    /**
     * 由历史商品实体与其意向名单构造（映射点）。
     *
     * <p>{@code price} 用 {@link java.math.BigDecimal#toPlainString()}：列类型 {@code decimal(8,2)}
     * 保证 scale=2，故恒为两位小数；<b>不</b>用数值类型（{@code 11-F}：JSON 数字会被 JS 解析成
     * IEEE-754 双精度）。
     */
    public static HistoryGoodsDetailData of(GoodsHistory goods, List<HistoryIntentionData> intentions) {
        return new HistoryGoodsDetailData(
                goods.getId(),
                goods.getName(),
                goods.getDescription(),
                goods.getPicUrl(),
                goods.getPrice().toPlainString(),
                goods.getStatus().name(),
                UtcIso8601.of(goods.getCreateAt()),
                UtcIso8601.of(goods.getTradeEnd()),
                goods.getResult().name(),
                intentions);
    }
}
