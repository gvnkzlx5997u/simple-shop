package com.simpleshop.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.simpleshop.persistence.enums.FreezeBy;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.time.DatabaseTimeProvider;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 商品（当前商品表 {@code simpleshop_goods}）。设计说明书 §4.1、§6.2；字典 §9.4.1。
 *
 * <h2>关键口径</h2>
 * <ul>
 *   <li>表内<b>至多 1 行</b>（{@code INV-01}）。该上限无法用 DDL 表达，由应用层
 *       在发布前调用 {@code GoodsRepository.existsByStatusIn(...)} 保障（{@code DEC-DB-06}）。</li>
 *   <li>{@code status} 在本表<b>只出现</b> {@code on_sale}／{@code frozen}；
 *       {@code off_sale} 只出现在历史表（{@code 9-H}）。</li>
 *   <li>{@link #result} 在本表<b>恒为 NULL</b>，实体保留映射以对齐字典，但<b>不得写入</b>
 *       （{@code DEC-DB-11}、§10.6）。</li>
 *   <li>{@code name}／{@code description}／{@code picUrl}／{@code price} 发布后<b>不可修改</b>
 *       （{@code BR-15}）。实体保留 setter（JPA 与归档复制需要），
 *       第二道防线由 {@code GoodsRepository} 不提供任何更新方法落实（§6.2.4）。</li>
 * </ul>
 */
@Entity
@Table(name = "simpleshop_goods")
public class Goods {

    /** 主键前缀：{@code G}（§10.7、{@code DEC-DB-04}）。 */
    private static final String ID_PREFIX = "G";

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "pic_url", length = 512)
    private String picUrl;

    @Column(name = "price", precision = 8, scale = 2, nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private GoodsStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_by", length = 10)
    private FreezeBy freezeBy;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "update_at", nullable = false)
    private LocalDateTime updateAt;

    /** 进入交易时由<b>业务显式赋值</b>，不在回调中自动写（§6.2.2）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_start")
    private LocalDateTime tradeStart;

    /** 归档时由<b>业务显式赋值</b>，不在回调中自动写（§6.2.2）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_end")
    private LocalDateTime tradeEnd;

    /**
     * ⚠️ <b>本表恒为 NULL</b>（{@code DEC-DB-11}、§10.6）。
     * 保留属性以对齐字典；归档时值写在 {@code GoodsHistory} 上，本表<b>任何路径都不写</b>。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10)
    private GoodsResult result;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected Goods() {
    }

    /**
     * 主键赋值：仅在 {@code id == null} 时生成（§10.7、{@code DEC-DB-04}）。
     *
     * <p><b>⚠️ 必须是 {@code if (id == null)}</b>：归档时历史行要沿用原 ID、
     * 由调用方显式赋原值；若无条件赋值会产生新 ID，直接违反 {@code 9-G}。
     */
    @PrePersist
    void assignIdAndTimestamps() {
        if (id == null) {
            id = ID_PREFIX + UUID.randomUUID();
        }
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        if (createAt == null) {
            createAt = now;   // 发布后永不修改
        }
        updateAt = now;
    }

    /** {@code updateAt} 在每次更新时刷新（状态变更即刷新，§6.2.2）。 */
    @PreUpdate
    void touchUpdateAt() {
        updateAt = DatabaseTimeProvider.utcNow();
    }

    public String getId() {
        return id;
    }

    /** 供归档复制等场景<b>显式</b>赋原值使用（不可用于「重新生成」）。 */
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public GoodsStatus getStatus() {
        return status;
    }

    public void setStatus(GoodsStatus status) {
        this.status = status;
    }

    public FreezeBy getFreezeBy() {
        return freezeBy;
    }

    public void setFreezeBy(FreezeBy freezeBy) {
        this.freezeBy = freezeBy;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
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

    /** ⚠️ 只读语义：本表恒为 NULL，不得写入（§10.6）。 */
    public GoodsResult getResult() {
        return result;
    }

    /** ⚠️ 仅为对齐字典而保留，<b>本阶段任何业务路径都不得调用</b>（§10.6）。 */
    public void setResult(GoodsResult result) {
        this.result = result;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Goods other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    /** 与 {@link #equals(Object)} 一致：{@code id == null} 时退化为对象身份哈希。 */
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "Goods{id='" + id + "', name='" + name + "', price=" + price
                + ", status=" + status + ", freezeBy=" + freezeBy
                + ", createAt=" + createAt + ", updateAt=" + updateAt
                + ", tradeStart=" + tradeStart + ", tradeEnd=" + tradeEnd + '}';
    }
}
