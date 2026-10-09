package com.simpleshop.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.simpleshop.persistence.enums.FreezeBy;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 历史商品（{@code simpleshop_goods_history}）。设计说明书 §4.3、§6.2.2；字典 §9.6.1。
 *
 * <h2>与 {@link Goods} 的关系</h2>
 * <p>字段<b>1:1 同名同类型</b>（{@code 9-C}），差异仅两处：{@code status} 恒为 {@code off_sale}、
 * {@code freeze_by} 恒为空。另有三处类型差异（见下表 {@code NOT NULL}）。
 *
 * <h2>⚠️ 本实体不挂任何自动时间回调</h2>
 * <p>归档是<b>数据搬迁</b>，不是业务更新：{@code createAt}／{@code updateAt}／{@code tradeStart}／
 * {@code tradeEnd} 必须<b>继承归档时的原值</b>，{@code updateAt} = 归档时间。
 * 若挂 {@code @PreUpdate}，二次保存会覆盖归档时间，污染历史数据（§6.2.2）。
 * 因此本类<b>既无 {@code @PrePersist} 也无 {@code @PreUpdate}</b>，
 * 全部字段由调用方在同一事务内<b>显式赋值</b>。
 *
 * <p><b>⚠️ 主键由调用方显式赋原商品的 ID</b>（前缀仍为 {@code G}——前缀表示「实体来源类型」，
 * 不表示当前所在表，{@code 9-G}、§10.7）。
 */
@Entity
@Table(name = "simpleshop_goods_history")
public class GoodsHistory {

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

    /** 归档时恒写 {@code off_sale}（应用层保证，数据库不设 CHECK）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private GoodsStatus status;

    /** 归档时恒清空（NULL）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_by", length = 10)
    private FreezeBy freezeBy;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    /** = 归档时间（UTC）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "update_at", nullable = false)
    private LocalDateTime updateAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_start")
    private LocalDateTime tradeStart;

    /** <b>必填</b>；手动下架时 = 下架时间。历史列表按此字段倒序分页（{@code I11-15}、{@code DEC-29}）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "trade_end", nullable = false)
    private LocalDateTime tradeEnd;

    /** <b>必填</b>：{@code sold}／{@code offline}。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10, nullable = false)
    private GoodsResult result;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected GoodsHistory() {
    }

    public String getId() {
        return id;
    }

    /** 归档时显式赋原商品 ID（不重新生成）。 */
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

    public GoodsResult getResult() {
        return result;
    }

    public void setResult(GoodsResult result) {
        this.result = result;
    }

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoodsHistory other)) {
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
        return "GoodsHistory{id='" + id + "', name='" + name + "', price=" + price
                + ", status=" + status + ", createAt=" + createAt + ", updateAt=" + updateAt
                + ", tradeStart=" + tradeStart + ", tradeEnd=" + tradeEnd
                + ", result=" + result + '}';
    }
}
