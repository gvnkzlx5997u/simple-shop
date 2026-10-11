package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 当前商品的响应数据（{@code I11-04}／{@code I11-07}／{@code I11-08} 的 {@code data}）。
 *
 * <p>依据：方案 §4.6.2 的逐字段清单。
 *
 * <h2>字段与契约的对应</h2>
 * <table border="1">
 *   <caption>9 个字段</caption>
 *   <tr><th>JSON 键</th><th>来源</th><th>可为 null</th></tr>
 *   <tr><td>{@code id}</td><td>{@code goods.id}</td><td>否</td></tr>
 *   <tr><td>{@code name}</td><td>{@code goods.name}</td><td>否</td></tr>
 *   <tr><td>{@code description}</td><td>{@code goods.description}</td><td><b>是</b>（§3.6：描述可选）</td></tr>
 *   <tr><td>{@code pic_url}</td><td>{@code goods.picUrl}</td><td><b>是</b>（未上传图片，前端显示占位图）</td></tr>
 *   <tr><td>{@code price}</td><td>{@code goods.price} → <b>字符串</b>两位小数</td><td>否</td></tr>
 *   <tr><td>{@code status}</td><td>{@code goods.status.name()} → {@code on_sale}／{@code frozen}</td><td>否</td></tr>
 *   <tr><td>{@code freeze_by}</td><td>{@code goods.freezeBy.name()} → {@code manual}／{@code trade}</td>
 *       <td><b>是</b>——{@code on_sale} 时必为 {@code null}</td></tr>
 *   <tr><td>{@code create_at}</td><td>ISO 8601 UTC</td><td>否</td></tr>
 *   <tr><td>{@code trade_start}</td><td>ISO 8601 UTC；<b>仅内部字段，页面不展示</b></td><td><b>是</b></td></tr>
 * </table>
 *
 * <h2>⚠️ {@code freeze_by} 必须下发（不是可有可无的内部字段）</h2>
 * <p>{@code P10-07} 靠它判断「解冻」按钮是否可用（{@code FR-04} 注、{@code PS-06}）：
 * 只有 {@code frozen} + {@code freeze_by = manual} 才允许点解冻；
 * {@code freeze_by = trade} 时按钮必须禁用，点了也应被服务端以 {@code 20005} 拒绝
 * （{@code FIX-01} 的服务端兜底防线）。
 * <p>若漏下发这个字段，前端只能「冻结就显示解冻按钮」，交易冻结期就会暴露一个必然失败的按钮。
 *
 * <h2>⚠️ {@code price} 是 JSON 字符串，不是数字（{@code 11-F}、{@code C-12}）</h2>
 * <p>用 {@link java.math.BigDecimal#toPlainString()} 而非数值类型：JSON 数字会被 JS 解析成
 * IEEE-754 双精度，{@code 999999.99} 这类值在往返后会变成 {@code 999999.9900000001} 之类。
 * 金额全程走字符串，比较与运算只在服务端用 {@code BigDecimal} 做。
 *
 * <h2>⚠️ 用 {@code record} 而不是带 setter 的类</h2>
 * <p>响应 DTO 是不可变值对象，其组件清单<b>就是</b>契约字段清单——编译期保证「不会多出字段」。
 * 这与方案 §4.6.4「映射必须显式列字段、不用反射式拷贝」的意图一致
 * （反射拷贝会静默把实体新增字段带进响应，违反 §11.1「接口层不得引入未登记字段」）。
 */
public record GoodsData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("pic_url") String picUrl,
        @JsonProperty("price") String price,
        @JsonProperty("status") String status,
        @JsonProperty("freeze_by") String freezeBy,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("trade_start") String tradeStart) {

    /**
     * 由实体构造（映射点）。
     *
     * <p>⚠️ 逐字段显式赋值，<b>不</b>用 {@code BeanUtils.copyProperties}。也<b>不</b>在这里碰
     * {@code goods.result}——该列恒为 NULL（{@code DEC-DB-11}），且契约未登记它。
     *
     * <p>枚举 → 字符串用 {@code name()}（{@code 9-Q}：枚举常量名<b>即</b>库中代码），
     * <b>不</b>下发 {@code getLabel()} 的中文（那是界面用词，由前端映射）。
     *
     * @param goods 当前商品实体（不得为 {@code null}；「无商品」由 Service 返回 {@code null} 表达）
     */
    public static GoodsData from(Goods goods) {
        return new GoodsData(
                goods.getId(),
                goods.getName(),
                goods.getDescription(),
                goods.getPicUrl(),
                // 列类型 decimal(8,2) 保证 scale=2，故 toPlainString 恒为两位小数
                goods.getPrice().toPlainString(),
                goods.getStatus().name(),
                goods.getFreezeBy() == null ? null : goods.getFreezeBy().name(),
                UtcIso8601.of(goods.getCreateAt()),
                UtcIso8601.of(goods.getTradeStart()));
    }
}
