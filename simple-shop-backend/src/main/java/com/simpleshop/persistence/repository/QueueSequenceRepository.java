package com.simpleshop.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.persistence.entity.QueueSequence;

import jakarta.persistence.LockModeType;

/**
 * 队列序号仓储（设计说明书 §7.8；{@code DEC-DB-03} 已定：建该仓储）。
 *
 * <h2>⚠️ 主键类型是 {@code Integer}，与其余仓储不同</h2>
 * <p>本表是单行辅助表，主键 {@code id} 固定为 {@code 1}（列类型 {@code tinyint}，
 * 实体侧用 {@code @JdbcTypeCode(SqlTypes.TINYINT)} 对齐）。
 * 因此本接口继承 {@code JpaRepository<QueueSequence, Integer>}——这是设计说明书 §7.1
 * 「一律继承 {@code JpaRepository<T, String>}」的<b>唯一例外</b>，由表结构决定。
 *
 * <h2>序号分配契约（§7.8，供 Service 遵守）</h2>
 * <pre>
 * 事务内:
 *   1. seq = findByIdForUpdate(1)          // 行锁：并发的第二个事务在此排队
 *   2. next = seq.getCurrentValue() + 1
 *   3. seq.setCurrentValue(next)
 *   4. save(seq)                            // 或依赖脏检查，事务提交时写回
 *   5. intention.setQueueOrder(next)
 *   → 提交：行锁释放，下一个事务拿到 next+1
 * </pre>
 *
 * <p>该契约保证：① 序号<b>单调递增</b>；② 序号<b>互不重复</b>（{@code uk_intention_queue_order}
 * 是第二道防线）；③ <b>不丢单</b>（后到者排队等待而非失败）；④ <b>不依赖意向表范围锁</b>，
 * 意向表插入互不阻塞。
 *
 * <p><b>⚠️ 必须在事务内调用</b>：{@code SELECT ... FOR UPDATE} 脱离事务时行锁立即释放，
 * 保护形同虚设（§7.8 末尾、§8.3）。
 */
public interface QueueSequenceRepository extends JpaRepository<QueueSequence, Integer> {

    /**
     * 按 ID <b>加排他锁</b>读取序号行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>调用方固定传 {@link QueueSequence#FIXED_ID}（= {@code 1}），在「提交意向」事务内分配序号。
     *
     * <p>加锁顺序遵循 §8.3：<b>① 序号表行 → ② 商品行 → ③ 意向行</b>（防死锁）。
     *
     * @param id 序号行固定 ID，传 {@code 1}
     * @return 序号行；未初始化时 {@link Optional#empty()}（此时应视为部署错误）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QueueSequence s where s.id = :id")
    Optional<QueueSequence> findByIdForUpdate(@Param("id") Integer id);
}
