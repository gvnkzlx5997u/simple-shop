package com.simpleshop.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.persistence.entity.TradeHistory;

/**
 * 意向交易流水仓储（设计说明书 §7.6）。主键为 {@code String}。
 *
 * <h2>⚠️ 本表只追加：不提供任何 delete / update 方法</h2>
 * <p>流水表一旦写入即不可变（§10.4「流的不可变性」、§4.5.3）。数据库层<b>不加</b>触发器阻止
 * {@code UPDATE}，该约束由<b>仓储层不提供 delete/update 派生方法</b>落实（§7.5）。
 *
 * <p>本接口因此只声明<b>查询</b>方法；继承自 {@link JpaRepository} 的
 * {@code delete*} 不在此列——约定即为「调用方不得使用」。
 *
 * <h2>⚠️ {@code intentionId} 是裸列，不是关联</h2>
 * <p>按 {@code 9-N}，{@code fk_intention_id} <b>不建物理外键</b>、实体也<b>不映射</b>
 * {@code @ManyToOne}（目标可能是当前意向表或历史意向表）。因此本仓储的方法一律以
 * <b>{@code String intentionId} 参数</b>入参，而不是 {@code Intention} 实体。
 *
 * <h2>排序口径</h2>
 * <p>按 {@code trade_start} <b>升序</b>即等于交易发生顺序（同一意向的多条流水其
 * {@code trade_start} 应单调递增）。{@code DEC-DB-10} 已定：<b>保留</b>该口径，
 * 不改为 {@code create_at}。
 */
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, String> {

    /**
     * 取某意向的全部流水，按 {@code trade_start} <b>升序</b>（= 交易发生顺序）。
     *
     * <p>用于 {@code I11-16} 的 {@code trades[]}（澄清 Q15、§9.7.3）。
     *
     * @param intentionId 意向 ID（当前表或历史表的 ID 均可，本列无物理外键）
     * @return 升序流水列表，可能为空
     */
    List<TradeHistory> findByIntentionIdOrderByTradeStartAsc(String intentionId);

    /**
     * 交易次数 N（某意向的流水条数）——<b>单条</b>查询。
     *
     * <p>用于 {@code I11-16} 的「交易次数：N」（§9.7.3）。
     *
     * <p><b>⚠️ 这是 N 次查询的方式</b>：对 N 条意向逐条调用即产生 N 次 SQL。
     * 若一次要取多条意向的次数，应改用
     * {@link #countGroupedByIntentionIdIn(Collection)}（1 次查询）。
     * 二者<b>都提供</b>，选哪种属业务层取舍（§7.6）。
     *
     * @param intentionId 意向 ID
     * @return 流水条数
     */
    long countByIntentionId(String intentionId);

    /**
     * 取某意向<b>最后一条</b>流水（按 {@code trade_start} 倒序首条）。
     *
     * <p>用于与意向当前状态做一致性核对（§9.7.2「最后一条流水应与意向状态对应」）。
     *
     * @param intentionId 意向 ID
     * @return 最后一条流水；无流水时 {@link Optional#empty()}
     */
    Optional<TradeHistory> findFirstByIntentionIdOrderByTradeStartDesc(String intentionId);

    /**
     * <b>批量</b>统计多条意向各自的流水数——一次查询取回「意向 → 次数」映射，避免 N+1。
     *
     * <p>历史商品详情会一次列出多条意向（{@code I11-16}）；若逐条调用
     * {@link #countByIntentionId(String)} 会产生 N 次查询（{@code 12-P1}：查询类 P95 ≤ 500ms）。
     *
     * <p><b>返回结构</b>：每个元素是一行 {@code Object[]}，长度为 2——
     * <pre>
     *   row[0] : String  intentionId（{@code fk_intention_id}）
     *   row[1] : Long    该意向的流水条数（{@code count} 在 MySQL 下为 Long）
     * </pre>
     *
     * <p><b>⚠️ 未产生任何流水的意向不会出现在结果中</b>（{@code group by} 的语义）：
     * 调用处应把「结果中缺失」视为 0，而不是当作错误。
     *
     * <p><b>⚠️ 列顺序是位置约定</b>：{@code select} 子句顺序一旦调整，{@code row[0]}／{@code row[1]}
     * 会静默错位。若希望编译期类型安全，可在后续版本改用投影接口
     * （如 {@code interface IntentionTradeCount { String getIntentionId(); long getTradeCount(); }}），
     * 语义完全等价——§7.6 已登记该可选项。
     *
     * <p><b>说明</b>：本方法对应 §7.6 的<b>勘误</b>——上一版签名为
     * {@code long countByIntentionIdIn(...)}，返回「总条数」，与其「取意向→次数映射」的动机矛盾。
     *
     * @param intentionIds 意向 ID 集合（当前表或历史表的 ID 均可，本列无物理外键）
     * @return 每行 {@code [intentionId, count]}；集合为空或均无流水时返回空列表
     */
    @Query("select t.intentionId, count(t) from TradeHistory t "
            + "where t.intentionId in :intentionIds group by t.intentionId")
    List<Object[]> countGroupedByIntentionIdIn(@Param("intentionIds") Collection<String> intentionIds);
}
