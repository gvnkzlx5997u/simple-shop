package com.simpleshop.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史商品列表的分页响应（{@code I11-15} 的 {@code data}）。依据：第 11 章 §11.5、方案 §4.6.2。
 *
 * <h2>⚠️ 与 {@link IntentionPageData} 是两份独立的 record，刻意不抽公共基类</h2>
 * <p>理由与 {@code IntentionPageData} 的类注释相同（record 不能被继承；想复用只剩嵌套或
 * {@code @JsonUnwrapped} 反射展开，而后者正是 §4.6.4 反对的隐式拷贝）。两者的差别也不止一个字段：
 * 本接口<b>没有</b> {@code queue_count}（那是意向名单特有的「当前非终态数」）。
 *
 * <h2>排序口径：{@code trade_end} 倒序，且<b>由仓储方法名内建</b></h2>
 * <p>{@code GoodsHistoryRepository.findAllByOrderByTradeEndDesc(pageable)} 的排序写在方法名里
 * （{@code DEC-29}）。⚠️ <b>调用方不得再传 {@code Sort}</b>——那会<b>覆盖</b>方法名里的排序，
 * 把「最近成交的排在前面」悄悄改掉。这与 {@code I11-10}（{@code findByGoodsId} 不内建排序、
 * 必须显式传）恰好相反，是本项目里唯一一处「显式传排序反而是错的」的地方。
 *
 * @param total    历史商品总数
 * @param page     当前页（<b>从 1 起</b>，回显请求值）
 * @param pageSize 每页条数（回显请求值；默认 10，见 {@code 10-E}）
 * @param items    本页的历史商品行，按 {@code trade_end} <b>倒序</b>
 */
public record HistoryPageData(
        @JsonProperty("total") long total,
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("items") List<HistoryGoodsItemData> items) {
}
