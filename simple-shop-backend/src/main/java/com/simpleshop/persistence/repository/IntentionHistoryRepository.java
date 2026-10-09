package com.simpleshop.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.persistence.entity.IntentionHistory;
import com.simpleshop.persistence.enums.IntentionStatus;

/**
 * 历史意向仓储（设计说明书 §7.5）。主键为 {@code String}。
 *
 * <h2>⚠️ 排序键是 {@code create_at}，<b>不是</b> {@code queue_order}</h2>
 * <p>按 {@code DEC-DB-05}，历史意向表<b>已删除 {@code queue_order} 列</b>（连同 {@code token}），
 * 因此历史名单只能按<b>意向提交时间 {@code create_at} 升序</b>排列（{@code BR-21}、§4.4.2）。
 * 语义上也更自然：历史名单反映的是<b>提交先后</b>，而非归档时的队列序号。
 *
 * <p>本实体<b>没有</b> {@code queueOrder}／{@code token} 属性，编译期即可杜绝误用。
 *
 * <h2>⚠️ {@code countActive} 的特殊角色</h2>
 * <p>原 {@code ck_intention_history_status_terminal} 已按 {@code DEC-DB-14} 删除（§10.3.2），
 * 于是 {@link #countActive(Collection)} 从「数据库兜底校验」变为
 * <b>{@code INV-05} 唯一的事后观测手段</b>——建议在集成测试中作为断言使用。
 */
public interface IntentionHistoryRepository extends JpaRepository<IntentionHistory, String> {

    /**
     * 取某历史商品下的意向名单，按 {@code create_at} <b>升序</b>。
     *
     * <p>用于 {@code I11-16} 的 {@code intentions[]}（{@code BR-21}）。
     *
     * <p><b>⚠️ 待同步事项</b>：{@code I11-16} 原本未规定排序字段，该「按 {@code create_at} 升序」
     * 的口径建议在接口文档中补一句（§11.4 已登记）。
     *
     * @param goodsHistoryId 历史商品 ID（派生属性 {@code goodsHistory.id}）
     * @return 按提交时间升序的历史意向列表
     */
    List<IntentionHistory> findByGoodsHistoryIdOrderByCreateAtAsc(String goodsHistoryId);

    /**
     * 某历史商品的意向名单分页（名单过长时使用）。
     *
     * <p><b>⚠️ 排序由调用方在 {@link Pageable} 中显式指定</b>（建议 {@code createAt ASC}），
     * 不依赖数据库默认顺序（§7.1「分页口径显式」）。
     *
     * @param goodsHistoryId 历史商品 ID
     * @param pageable       分页与排序参数（必须含排序）
     * @return 分页结果
     */
    Page<IntentionHistory> findByGoodsHistoryId(String goodsHistoryId, Pageable pageable);

    /**
     * 统计某历史商品下的意向条数，用于<b>归档完整性核对</b>
     * （{@code NFR-08}「归档前后计数一致」）。
     *
     * @param goodsHistoryId 历史商品 ID
     * @return 条数
     */
    long countByGoodsHistoryId(String goodsHistoryId);

    /**
     * 统计历史意向表中仍处于「活动状态」的记录数——<b>应恒为 0</b>。
     *
     * <p>{@code INV-05} 的事后断言：历史意向的 {@code status} 必须是终态。
     * 由于数据库级 {@code CHECK} 已删除，本方法是<b>唯一</b>的观测手段。
     *
     * @param activeStatuses 活动状态集合，通常为 {@code {queued, trading}}
     * @return 违反 {@code INV-05} 的记录数；正确归档后应为 {@code 0}
     */
    @Query("select count(i) from IntentionHistory i where i.status in :activeStatuses")
    long countActive(@Param("activeStatuses") Collection<IntentionStatus> activeStatuses);
}
