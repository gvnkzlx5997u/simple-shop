package com.simpleshop.persistence.entity;

import java.time.LocalDateTime;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.IntentionStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 历史意向（{@code simpleshop_intentions_history}）。设计说明书 §4.4；字典 §9.6.2。
 *
 * <h2>⚠️ 与 {@link Intention} <b>不是</b> 1:1 复制</h2>
 * <p>按 {@code DEC-DB-05}，本表<b>不含 {@code queue_order} 与 {@code token}</b> 两列。差异共三处：
 * <ol>
 *   <li>外键列<b>改名</b>：{@code fk_good_id} → {@code fk_goods_history_id}（值不变）；</li>
 *   <li><b>删除 {@code queue_order}</b> —— 位次是过程性辅助数据，商品下架后已无意义；</li>
 *   <li><b>删除 {@code token}</b> —— 意向进入终态口令码即失效（{@code BR-26}），已失效凭证无保留价值。</li>
 * </ol>
 * 附带收益：两列只存在于当前表，「全局唯一」由单表唯一索引真正落实（§10.1 缺口消解）。
 *
 * <h2>归档列对应关系（供 Service 参考，§4.4.3）</h2>
 * <p>{@code id}、{@code createAt}、{@code name}、{@code tel}、{@code status}、{@code failType}、
 * {@code failReason} <b>逐列复制</b>；{@code fk_good_id} → {@code fk_goods_history_id}；
 * <b>{@code queueOrder} 与 {@code token} 不复制</b>。
 *
 * <h2>⚠️ 本实体不挂任何自动时间回调</h2>
 * <p>归档是数据搬迁，时间列必须继承原值（§6.2.2）；主键由调用方显式赋原意向的 ID
 * （前缀仍为 {@code I}）。
 *
 * <h2>⚠️ {@code toString()} 不得输出 {@code token}</h2>
 * <p>本实体虽无 {@code token} 列，仍遵循 §6.2.1 的同一约定
 * （{@code NFR-12}：日志不记录口令码明文），避免将来加字段时误泄漏。
 */
@Entity
@Table(name = "simpleshop_intentions_history")
public class IntentionHistory {

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    /**
     * 所属历史商品。外键列名不规则，<b>必须显式声明</b> {@code @JoinColumn}（§2.3）。
     * 抓取策略 {@code LAZY}、不级联（§6.2.1）。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_goods_history_id", nullable = false)
    private GoodsHistory goodsHistory;

    /** 原始提交时间（UTC）。<b>历史名单的排序键</b>（按 {@code create_at} 升序，非 {@code queue_order}，§4.4.2）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "tel", length = 20, nullable = false)
    private String tel;

    /** 归档时<b>必为终态</b>（{@code succeeded}／{@code failed}／{@code revoked}）——
     * 即 {@code INV-05}。该约束已按 {@code DEC-DB-14} 下沉为应用层归档前置校验 + 测试断言。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private IntentionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_type", length = 10)
    private FailType failType;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected IntentionHistory() {
    }

    public String getId() {
        return id;
    }

    /** 归档时显式赋原意向 ID（不重新生成）。 */
    public void setId(String id) {
        this.id = id;
    }

    public GoodsHistory getGoodsHistory() {
        return goodsHistory;
    }

    public void setGoodsHistory(GoodsHistory goodsHistory) {
        this.goodsHistory = goodsHistory;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public IntentionStatus getStatus() {
        return status;
    }

    public void setStatus(IntentionStatus status) {
        this.status = status;
    }

    public FailType getFailType() {
        return failType;
    }

    public void setFailType(FailType failType) {
        this.failType = failType;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntentionHistory other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    /** ⚠️ 遵循「不得输出 {@code token}」的约定（{@code NFR-12}、§6.2.1）；本实体无该列。 */
    @Override
    public String toString() {
        return "IntentionHistory{id='" + id + "', createAt=" + createAt
                + ", name='" + name + "', tel='" + tel + "', status=" + status
                + ", failType=" + failType + ", failReason='" + failReason + "'}";
    }
}
