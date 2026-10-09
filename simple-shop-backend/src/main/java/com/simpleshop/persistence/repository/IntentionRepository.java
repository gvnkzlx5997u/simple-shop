package com.simpleshop.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.enums.IntentionStatus;

import jakarta.persistence.LockModeType;

/**
 * 购买意向仓储（设计说明书 §7.3）。主键为 {@code String}。
 *
 * <h2>方法族</h2>
 * <ul>
 *   <li><b>队列视图</b>：队首、队列升序全量、名单分页；</li>
 *   <li><b>位次与计数</b>：{@code countQueuedAhead}（当前排第 N 位）、
 *       {@code countNonTerminal}（队列容量口径）、{@code countByGoodsIdAndStatus}；</li>
 *   <li><b>口令码</b>：{@code findByToken} 与加锁版；</li>
 *   <li><b>加锁</b>：{@code findByIdForUpdate}、{@code findByTokenForUpdate}；</li>
 *   <li><b>归档前置校验</b>：{@code countActiveByGoodsId}（{@code INV-05}）。</li>
 * </ul>
 *
 * <h2>⚠️ 刻意不提供的方法</h2>
 * <p>不提供「队列是否已满」的判定方法，也不提供任何抛业务异常的入口（§7.1）。
 * 队列容量比对（上限 1000，{@code NFR-07}）由 Service 用 {@code countNonTerminal} 的结果自行判定。
 *
 * <h2>加锁契约（§8.3）</h2>
 * <p>意向行锁用于「卖家对某意向登记交易结果」与「买家凭口令码撤销／改信息」。
 * 与商品行锁<b>配合</b>使用时，必须遵守统一加锁顺序
 * <b>① 序号表行 → ② 商品行 → ③ 意向行</b>，否则可能死锁。
 * <b>所有 {@code ...ForUpdate} 方法都必须在事务内调用</b>。
 *
 * <h2>⚠️ 口令码的归一化口径（§7.3）</h2>
 * <p>库内 {@code token} 值<b>恒为大写</b>，由<b>应用层</b>在生成与写入时保证；
 * 查询参数须由 Service <b>先去首尾空格、再转大写</b>后传入（{@code R12-06}、{@code 10-G}、
 * {@code DEC-DB-08}）。当前表的排序规则 {@code utf8mb4_unicode_ci} 虽不区分大小写，
 * <b>不得</b>把它当作正确性依赖——归一化才是主路径。
 */
public interface IntentionRepository extends JpaRepository<Intention, String> {

    // -------------------------------------------------------------------------
    // 队列视图
    // -------------------------------------------------------------------------

    /**
     * 取队首：某商品下指定状态中 {@code queue_order} 最小的一条。
     *
     * <p>用于 {@code I11-11} → {@code 30002}「非队首拒绝」（{@code BR-12}、§9.5.3）。
     *
     * @param goodsId 商品 ID（派生属性 {@code goods.id}）
     * @param status  目标状态，通常为 {@code queued}
     * @return 队首意向；无匹配时 {@link Optional#empty()}
     */
    Optional<Intention> findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(String goodsId, IntentionStatus status);

    /**
     * 取某商品下指定状态的<b>全量</b>队列，按 {@code queue_order} 升序。
     *
     * <p>用于「标记成功时批量把其余 {@code queued} 置为失败」（{@code BR-03}、{@code BR-04}），
     * 以及重新排队后重算位次。
     *
     * @param goodsId 商品 ID
     * @param status  目标状态，通常为 {@code queued}
     * @return 升序队列，可能为空列表
     */
    List<Intention> findByGoodsIdAndStatusOrderByQueueOrderAsc(String goodsId, IntentionStatus status);

    /**
     * 取某商品的<b>全部</b>意向（含终态与非终态），按 {@code queue_order} 升序。
     *
     * <p>用于<b>归档整体迁出</b>（{@code DEC-04}、§9.6.3）：归档需要一次性拿到全部意向，
     * 便于在归档前校验 {@code INV-05} 并逐条复制到历史表。
     *
     * @param goodsId 商品 ID
     * @return 升序全量意向
     */
    List<Intention> findByGoodsIdOrderByQueueOrderAsc(String goodsId);

    /**
     * 某商品意向名单分页（{@code I11-10}）。
     *
     * <p><b>⚠️ 排序由调用方在 {@link Pageable} 中显式指定</b>（建议 {@code queueOrder ASC}）——
     * 本方法不内建 {@code OrderBy}，以保持与 §7.3 的签名一致；
     * 调用方<b>不得</b>依赖数据库默认顺序（§7.1「分页口径显式」）。
     *
     * @param goodsId  商品 ID
     * @param pageable 分页与排序参数（必须含排序）
     * @return 分页结果
     */
    Page<Intention> findByGoodsId(String goodsId, Pageable pageable);

    // -------------------------------------------------------------------------
    // 位次与计数
    // -------------------------------------------------------------------------

    /**
     * 位次（秩）：统计某商品下 {@code queued} 且 {@code queue_order} 更小的意向条数。
     *
     * <p><b>位次 = 返回值 + 1</b>，用于 {@code FR-15}「当前排第 N 位」（§9.5.3）。
     *
     * <p><b>撤销后位次自动前移</b>：位次是<b>计数派生</b>，不需要额外的「前移」SQL
     * （{@code BR-19}、§7.3 的「无需额外方法」）。
     *
     * @param goodsId    商品 ID
     * @param queueOrder 目标意向的序号
     * @return 排在其前面的 {@code queued} 条数
     */
    @Query("select count(i) from Intention i where i.goods.id = :goodsId "
            + "and i.status = com.simpleshop.persistence.enums.IntentionStatus.queued "
            + "and i.queueOrder < :queueOrder")
    long countQueuedAhead(@Param("goodsId") String goodsId, @Param("queueOrder") int queueOrder);

    /**
     * 非终态计数（指定商品）：队列容量口径。
     *
     * <p>用于 {@code NFR-07}（队列上限 1000）与 {@code I11-10} 的 {@code queue_count}
     * （{@code DEC-23}、§9.5.2）。
     *
     * <p><b>⚠️ 只返回计数，不判定是否超限</b>：是否拒绝由 Service 决定（§7.1）。
     *
     * @param goodsId          商品 ID
     * @param terminalStatuses 终态集合，通常为 {@code {succeeded, failed, revoked}}
     * @return 非终态意向条数
     */
    @Query("select count(i) from Intention i where i.goods.id = :goodsId and i.status not in :terminalStatuses")
    long countNonTerminal(@Param("goodsId") String goodsId,
                          @Param("terminalStatuses") Collection<IntentionStatus> terminalStatuses);

    /**
     * 非终态计数（<b>全表</b>）：不变量巡检辅助。
     *
     * <p>供归档后核验「当前表中已无 {@code queued}／{@code trading}」使用（{@code INV-05}、§8.4）。
     *
     * @param terminalStatuses 终态集合
     * @return 全表非终态意向条数
     */
    @Query("select count(i) from Intention i where i.status not in :terminalStatuses")
    long countNonTerminal(@Param("terminalStatuses") Collection<IntentionStatus> terminalStatuses);

    /**
     * 按商品与状态计数。例如统计某商品的 {@code succeeded} 条数（{@code INV-06} 判定辅助）。
     *
     * @param goodsId 商品 ID
     * @param status  目标状态
     * @return 条数
     */
    long countByGoodsIdAndStatus(String goodsId, IntentionStatus status);

    // -------------------------------------------------------------------------
    // 口令码
    // -------------------------------------------------------------------------

    /**
     * 按口令码取意向。{@code token} 全局唯一（单表唯一索引），故至多 1 条。
     *
     * <p>用于 {@code FR-22} 的三态校验（{@code BR-25}、{@code S7-02}）。
     *
     * <p><b>⚠️ 传入前须归一化</b>：去首尾空格 + 转大写（§7.3）。
     *
     * @param token 归一化后的口令码
     * @return 意向；无匹配时 {@link Optional#empty()}
     */
    Optional<Intention> findByToken(String token);

    /**
     * 按口令码<b>加排他锁</b>取意向（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>用于买家凭口令码<b>撤销</b>或<b>改信息</b>，防「撤销」与「进入交易」并发导致状态错乱（§8.3）。
     * <b>必须在事务内调用。</b>
     *
     * @param token 归一化后的口令码
     * @return 意向；无匹配时 {@link Optional#empty()}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intention i where i.token = :token")
    Optional<Intention> findByTokenForUpdate(@Param("token") String token);

    // -------------------------------------------------------------------------
    // 加锁与归档校验
    // -------------------------------------------------------------------------

    /**
     * 按 ID <b>加排他锁</b>取意向（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>用于卖家对某意向发起交易结果登记、以及状态裁决（§8.2、§8.3）。
     * 与商品行锁配合时，遵守统一加锁顺序：序号表行 → 商品行 → 意向行。
     * <b>必须在事务内调用。</b>
     *
     * @param id 意向 ID
     * @return 意向；不存在时 {@link Optional#empty()}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intention i where i.id = :id")
    Optional<Intention> findByIdForUpdate(@Param("id") String id);

    /**
     * 统计某商品下仍处于「活动状态」的意向条数——<b>归档事务内的 {@code INV-05} 前置校验</b>。
     *
     * <p>归档要求「随商品迁出的意向必须全部是终态」。若本方法返回值 <b>&gt; 0</b>，
     * 说明仍有 {@code queued}／{@code trading} 意向，归档应被拒绝
     * （{@code INV-05}、{@code I11-12} → {@code 30005}）。
     *
     * <p><b>⚠️ 本方法只报数，不抛异常、不做拒绝</b>——拒绝由 Service 决定（§7.1）。
     *
     * @param goodsId       商品 ID
     * @param activeStatuses 活动状态集合，通常为 {@code {queued, trading}}
     * @return 仍处于活动状态的意向条数
     */
    @Query("select count(i) from Intention i where i.goods.id = :goodsId and i.status in :activeStatuses")
    long countActiveByGoodsId(@Param("goodsId") String goodsId,
                              @Param("activeStatuses") Collection<IntentionStatus> activeStatuses);
}
