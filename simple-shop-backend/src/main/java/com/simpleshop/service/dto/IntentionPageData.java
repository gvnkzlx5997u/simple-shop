package com.simpleshop.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 意向名单的分页响应（{@code I11-10} 的 {@code data}）。依据：第 11 章 §11.5、方案 §4.6.2。
 *
 * <h2>字段</h2>
 * <p>分页四件套（{@code total}／{@code page}／{@code page_size}／{@code items}）
 * 加上意向名单特有的 {@code queue_count}。
 *
 * <h2>⚠️ {@code queue_count} 的判定式是「非终态」，与「队列长度」不是一回事</h2>
 * <p>{@code 10-D}／{@code DEC-23} 要的是「当前队列计数，上限 1000」，口径为
 * {@code {succeeded, failed, revoked}} 之外的行数——即 {@code queued} + {@code trading}，
 * 与 {@code submitIntention} 里判队列是否已满所<b>用同一个</b>计数方法
 * （{@code countNonTerminal}）。两者必须一致，否则会出现「名单显示 1000，
 * 但提交又说没满」这种自相矛盾。
 *
 * <h2>⚠️ 为什么本类不抽一个公共的 {@code PageData<T>} 基类</h2>
 * <p>方案 §4.6.2 的草图里 {@code IntentionPageData}「继承 {@code PageData} 的 4 字段」，
 * 且注明 {@code PageData} 供 {@code I11-10}／{@code I11-15} 复用。实际实现<b>没有</b>那么做：
 * <ul>
 *   <li>响应 DTO 一律是 {@code record}（不可变、组件清单即契约字段清单，见 {@code GoodsData} 的说明），
 *       而 <b>record 不能被继承</b>——想让 {@code IntentionPageData} 复用 {@code PageData} 的字段，
 *       只剩「组合成嵌套对象」（JSON 会多一层，契约不符）或
 *       「{@code @JsonUnwrapped} 反射展开」（正是 §4.6.4 反对的隐式拷贝）。</li>
 *   <li>代价也只是<b>四行字段声明</b>（{@code I11-15} 的 {@code HistoryPageData} 才需要第二份），
 *       而收益是每一份响应 DTO 都能被一眼核对完——这正是 §4.6.4 想要的性质。</li>
 * </ul>
 * <p>故此处保留独立的 5 字段记录。若日后第三个分页接口出现、或有人改成非 record 的类层次，
 * 再抽基类也不迟。
 *
 * @param total      意向总数（<b>含终态</b>——它是「这个商品一共有过多少条意向」，不是队列长度）
 * @param page       当前页（<b>从 1 起</b>，回显请求值）
 * @param pageSize   每页条数（回显请求值）
 * @param queueCount 当前非终态意向数（{@code queued} + {@code trading}，上限 1000）
 * @param items      本页的意向行，按 {@code queue_order} 升序
 */
public record IntentionPageData(
        @JsonProperty("total") long total,
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("queue_count") int queueCount,
        @JsonProperty("items") List<IntentionItemData> items) {
}
