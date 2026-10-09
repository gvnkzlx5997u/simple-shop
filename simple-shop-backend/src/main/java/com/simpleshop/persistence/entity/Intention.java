package com.simpleshop.persistence.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.time.DatabaseTimeProvider;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 购买意向（当前意向表 {@code simpleshop_intentions}）。设计说明书 §4.2、§6.2；字典 §9.5.1。
 *
 * <h2>关键口径</h2>
 * <ul>
 *   <li>{@code queueOrder} <b>全库唯一</b>，由 {@code simpleshop_queue_sequence} 在事务内分配，
 *       重新排队时刷新（{@code BR-20}、{@code DEC-15}）。</li>
 *   <li>{@code createAt} 为<b>原始提交时间</b>，写入后<b>不再变更</b>——
 *       重排队、改姓名电话均不得修改（{@code BR-20}、{@code DEC-15}、§6.2.2）。
 *       因此本类<b>只有 {@code @PrePersist}，没有 {@code @PreUpdate}</b>。</li>
 *   <li>{@code token} 口令码 {@code [A-Z0-9]{12}}，全局唯一、可反查（{@code BR-25}、{@code S7-01}）。
 *       其生成属应用层（系统唯一需要自定义生成逻辑的字段，§10.7），本实体只负责持有。</li>
 *   <li><b>⚠️ {@code toString()} 不得输出 {@code token}</b>（{@code NFR-12}、§6.2.1）：
 *       「日志不记录口令码明文」。</li>
 * </ul>
 */
@Entity
@Table(name = "simpleshop_intentions")
public class Intention {

    /** 主键前缀：{@code I}（§10.7）。归档到历史意向表时<b>沿用该前缀与原值</b>（{@code 9-G}）。 */
    private static final String ID_PREFIX = "I";

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    /**
     * 所属商品。外键列名不规则（{@code fk_good_id}，单数 {@code good}），
     * <b>必须显式声明</b> {@code @JoinColumn}（§2.3）。
     * 抓取策略 {@code LAZY}、不级联（§6.2.1）。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_good_id", nullable = false)
    private Goods goods;

    @Column(name = "queue_order", nullable = false, unique = true)
    private Integer queueOrder;

    /** 原始提交时间（UTC）。重排队与改姓名电话时<b>均不得变更</b>（{@code BR-20}）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "tel", length = 20, nullable = false)
    private String tel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private IntentionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_type", length = 10)
    private FailType failType;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    /** 口令码，唯一、可反查。⚠️ 不得出现在 {@code toString()}。 */
    @Column(name = "token", length = 20, nullable = false, unique = true)
    private String token;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected Intention() {
    }

    /**
     * 主键赋值：仅在 {@code id == null} 时生成（§10.7）。
     *
     * <p><b>⚠️ 必须是 {@code if (id == null)}</b>：归档时由调用方显式赋原 ID，
     * 无条件赋值会破坏 {@code 9-G}。
     *
     * <p>本回调<b>只写 {@code createAt} 与 {@code id}</b>，不写 {@code updateAt}
     * ——本表没有 {@code update_at} 列，且 {@code createAt} 一经写入不得再改（§6.2.2）。
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

    /** 供归档复制等场景<b>显式</b>赋原值使用。 */
    public void setId(String id) {
        this.id = id;
    }

    public Goods getGoods() {
        return goods;
    }

    public void setGoods(Goods goods) {
        this.goods = goods;
    }

    public Integer getQueueOrder() {
        return queueOrder;
    }

    public void setQueueOrder(Integer queueOrder) {
        this.queueOrder = queueOrder;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    /** ⚠️ 仅供归档复制/JPA 使用；业务上<b>不得</b>用它修改原始提交时间（{@code BR-20}）。 */
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Intention other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    /** ⚠️ <b>刻意不输出 {@code token}</b>（{@code NFR-12}、§6.2.1）。 */
    @Override
    public String toString() {
        return "Intention{id='" + id + "', queueOrder=" + queueOrder
                + ", createAt=" + createAt + ", name='" + name + "', tel='" + tel
                + "', status=" + status + ", failType=" + failType
                + ", failReason='" + failReason + "'}";
    }
}
