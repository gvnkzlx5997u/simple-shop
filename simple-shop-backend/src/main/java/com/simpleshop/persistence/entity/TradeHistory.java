package com.simpleshop.persistence.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.TradeResult;
import com.simpleshop.persistence.time.DatabaseTimeProvider;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 意向交易流水（{@code simpleshop_trade_history}）。设计说明书 §4.5、§7.5；字典 §9.7。
 *
 * <h2>关键口径</h2>
 * <ul>
 *   <li><b>⚠️ {@code intentionId} 映射为普通 {@code String} 列，不映射 {@code @ManyToOne}</b>
 *       （§4.5.1、{@code 9-N}）。理由：① 本表<b>刻意不建物理外键</b>——归档会移动意向行，
 *       物理外键在归档事务内会瞬时失配，且 {@code ON DELETE} 语义会误删流水；
 *       ② 目标可能是 {@code simpleshop_intentions} 或 {@code simpleshop_intentions_history}
 *       两张表，归档前后目标不同，映射成关联会在归档瞬间产生「悬空引用」的加载异常。
 *       查询流水一律用 {@code findByIntentionIdOrderByTradeStartAsc(...)}。</li>
 *   <li>本表<b>只追加</b>，不更新、不删除（§10.4）。数据库层不加触发器阻止 UPDATE，
 *       由 {@code TradeHistoryRepository} 不提供任何 delete/update 派生方法落实（§7.5）。</li>
 *   <li>{@code createAt} 是流水<b>追加写入</b>时间（技术字段），由 {@code @PrePersist} 写入；
 *       {@code tradeStart}／{@code tradeEnd} 是<b>业务显式赋值</b>（§6.2.2）。</li>
 * </ul>
 */
@Entity
@Table(name = "simpleshop_trade_history")
public class TradeHistory {

    /** 主键前缀：{@code TH}（§10.7）。 */
    private static final String ID_PREFIX = "TH";

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    /**
     * 意向编号。<b>裸 {@code String} 列，无物理外键、无 {@code @ManyToOne}</b>（§4.5.1、{@code 9-N}）。
     * 列宽与意向主键一致（{@code varchar(50)}），以便将来需要时可比对或补建外键。
     */
    @Column(name = "fk_intention_id", length = 50, nullable = false)
    private String intentionId;

    /** 该次「进入交易」的时间戳（UTC），业务显式赋值。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_start", nullable = false)
    private LocalDateTime tradeStart;

    /** 该次「标记交易结果」的时间戳（UTC），业务显式赋值。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_end", nullable = false)
    private LocalDateTime tradeEnd;

    /** <b>流水级</b>结果：{@code sold}／{@code failed}（§9.9.6）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10, nullable = false)
    private TradeResult result;

    /** {@code result = failed} 时必填；该「条件必填」由应用层保证（数据库可空，§10.2）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "fail_type", length = 10)
    private FailType failType;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    /** 流水追加写入时间（UTC），技术字段。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected TradeHistory() {
    }

    /**
     * 主键赋值：仅在 {@code id == null} 时生成（§10.7）。
     *
     * <p>只写 {@code id} 与 {@code createAt}；{@code tradeStart}／{@code tradeEnd}
     * <b>由业务显式赋值</b>，不在回调中写（§6.2.2）。
     */
    @PrePersist
    void assignIdAndCreateAt() {
        if (id == null) {
            id = ID_PREFIX + UUID.randomUUID();
        }
        if (createAt == null) {
            createAt = DatabaseTimeProvider.utcNow();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIntentionId() {
        return intentionId;
    }

    public void setIntentionId(String intentionId) {
        this.intentionId = intentionId;
    }

    public LocalDateTime getTradeStart() {
        return tradeStart;
    }

    public void setTradeStart(LocalDateTime tradeStart) {
        this.tradeStart = tradeStart;
    }

    public LocalDateTime getTradeEnd() {
        return tradeEnd;
    }

    public void setTradeEnd(LocalDateTime tradeEnd) {
        this.tradeEnd = tradeEnd;
    }

    public TradeResult getResult() {
        return result;
    }

    public void setResult(TradeResult result) {
        this.result = result;
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

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TradeHistory other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    /** ⚠️ <b>不得输出 {@code token}</b>——本实体无该列，但同样遵守 §6.2.1 的约定。 */
    @Override
    public String toString() {
        return "TradeHistory{id='" + id + "', intentionId='" + intentionId
                + "', tradeStart=" + tradeStart + ", tradeEnd=" + tradeEnd
                + ", result=" + result + ", failType=" + failType
                + ", failReason='" + failReason + "', createAt=" + createAt + '}';
    }
}
