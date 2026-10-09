package com.simpleshop.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 队列序号辅助表（{@code simpleshop_queue_sequence}）。设计说明书 §4.7；{@code DEC-DB-03}。
 *
 * <h2>用途</h2>
 * <p>为 {@code queue_order} 提供<b>全库单调自增</b>的序号来源。字典 §9.5.2 只规定
 * 「唯一、自增、事务内分配」，未规定实现载体；本表把「序号分配」从「意向行」中解耦，
 * 使「重新排队刷新序号」成为一次纯计数操作，且不依赖对意向表加范围锁（§4.7）。
 *
 * <h2>性质与约束</h2>
 * <ul>
 *   <li><b>辅助表</b>：不承载业务数据、不被任何业务表引用，因此<b>归档时无需处理它</b>。</li>
 *   <li><b>单行表</b>：{@code id} 固定为 {@code 1}。</li>
 *   <li>{@code currentValue} <b>只增不减</b>，保存「已分配的最大 {@code queue_order}」，初值 {@code 0}。
 *       初始行由 V2 初始化脚本用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 幂等写入。</li>
 *   <li>⚠️ 备份范围<b>须包含本表</b>（{@code OPEN-02}）：否则恢复后序号从 0 重来，
 *       会与既有 {@code queue_order} 冲突——该冲突由 {@code uk_intention_queue_order} 唯一索引
 *       拦截并报错，属<b>可发现</b>的失败，不会静默产生重复。</li>
 *   <li>序号分配必须在<b>事务内</b>对 {@code id = 1} 的该行加排他锁后进行
 *       （{@code QueueSequenceRepository.findByIdForUpdate(1)}，§8.3）。</li>
 * </ul>
 *
 * <p><b>注意</b>：本实体的主键是 {@code Integer}（列类型 {@code tinyint}），
 * 与其余实体不同；相应地其仓储为 {@code JpaRepository<QueueSequence, Integer>}——
 * 但按 §7.8 的使用口径，仓储仍以固定 ID {@code 1} 取行。
 *
 * <p>本实体<b>无时间字段</b>，故不挂任何生命周期回调，也无需生成主键
 * （ID 是固定的业务常量 {@code 1}，不是 UUID）。
 */
@Entity
@Table(name = "simpleshop_queue_sequence")
public class QueueSequence {

    /** 单行表的固定主键值（§4.7）。 */
    public static final int FIXED_ID = 1;

    /**
     * ⚠️ <b>必须显式声明 {@code @JdbcTypeCode(SqlTypes.TINYINT)}</b>。
     *
     * <p>字典 §9.5.2 与 §4.7 把本列定义为 {@code tinyint}，而 Java 侧按 §4.7 的
     * 「Java 类型 = {@code Integer}」保留。Hibernate 6 对 {@code Integer} 的默认 JDBC 类型是
     * {@code integer}，与库中的 {@code tinyint} 不符，{@code ddl-auto=validate} 会直接报
     * 「wrong column type encountered in column [id] in table [simpleshop_queue_sequence]」。
     * 该注解把映射落到 {@code tinyint}，使实体与库结构一致。
     *
     * <p>不使用 {@code @Column(columnDefinition = "tinyint")}：那会干扰 validate 的比对，
     * 且 {@code columnDefinition} 属「DDL 生成」用途，与「库里已有类型」的声明无关。
     */
    @Id
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "id", nullable = false)
    private Integer id;

    /** 已分配的最大 {@code queue_order}；只增不减，初值 {@code 0}。 */
    @Column(name = "current_value", nullable = false)
    private Long currentValue;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected QueueSequence() {
    }

    /** 便捷构造：固定 ID = {@code 1}（供初始化脚本／测试使用）。 */
    public QueueSequence(Long currentValue) {
        this.id = FIXED_ID;
        this.currentValue = currentValue;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Long currentValue) {
        this.currentValue = currentValue;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueueSequence other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "QueueSequence{id=" + id + ", currentValue=" + currentValue + '}';
    }
}
