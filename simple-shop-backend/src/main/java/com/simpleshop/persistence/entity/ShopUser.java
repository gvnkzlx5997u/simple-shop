package com.simpleshop.persistence.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.simpleshop.persistence.time.DatabaseTimeProvider;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 卖家账号（{@code simpleshop_users}）。设计说明书 §4.6；字典 §9.8。
 *
 * <h2>命名说明</h2>
 * <p>字典表名为 {@code simpleshop_users}，而 {@code User} 是 SQL 保留字且易与框架内建类型混淆，
 * 故实体类命名为 {@code ShopUser}，用 {@code @Table(name = "simpleshop_users")} 显式落表（§4.6.1）。
 * 这是<b>命名取径</b>，不改变任何字段口径。
 *
 * <h2>关键口径</h2>
 * <ul>
 *   <li>{@code password} 必须是<b>不可逆哈希</b>（建议 BCrypt），<b>禁止明文</b>（{@code C-17}、{@code DEC-06}）。
 *       哈希算法属应用层，本文档只规定列宽 {@code varchar(100)} 与「不可逆」。
 *       数据库侧有 {@code ck_user_password_not_blank}（{@code password <> ''}）作为最低物理保障。</li>
 *   <li>{@code updateAt} 语义为「密码<b>最后修改</b>时间」。按 {@code DEC-DB-10} 暂用 {@code @PreUpdate}：
 *       当前实体只有 {@code password} 一个可变字段，故语义等价。
 *       <b>⚠️ 若将来新增可变字段，必须改为显式赋值</b>，否则无关字段的更新会带动该列（§6.2.2）。</li>
 *   <li>仅需单一账号（{@code FR-01}）；{@code account} 全局唯一。</li>
 * </ul>
 */
@Entity
@Table(name = "simpleshop_users")
public class ShopUser {

    /** 主键前缀：{@code U}（§10.7）。 */
    private static final String ID_PREFIX = "U";

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Column(name = "account", length = 50, nullable = false, unique = true)
    private String account;

    /** 不可逆哈希（BCrypt 编码为 60 字符，{@code varchar(100)} 有余量）。⚠️ 禁止明文。 */
    @Column(name = "password", length = 100, nullable = false)
    private String password;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    /** 密码最后修改时间（UTC）。⚠️ 新增可变字段后须改为显式赋值（{@code DEC-DB-10}、§6.2.2）。 */
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "update_at", nullable = false)
    private LocalDateTime updateAt;

    /** JPA 规范要求的 {@code protected} 无参构造；类不可为 {@code final}（§6.2.1）。 */
    protected ShopUser() {
    }

    /**
     * 主键赋值：仅在 {@code id == null} 时生成（§10.7）。
     * {@code createAt} 一次性写入，之后不变。
     */
    @PrePersist
    void assignIdAndTimestamps() {
        if (id == null) {
            id = ID_PREFIX + UUID.randomUUID();
        }
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        if (createAt == null) {
            createAt = now;
        }
        updateAt = now;
    }

    /** ⚠️ 见类注释：当前与「仅改密时更新」语义等价；新增可变字段后须改为显式赋值。 */
    @PreUpdate
    void touchUpdateAt() {
        updateAt = DatabaseTimeProvider.utcNow();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    /** 按 {@code id} 实现；{@code id == null} 时按对象同一性（§6.2.1）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShopUser other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    /** ⚠️ <b>不输出 {@code password}</b>——哈希亦不应进日志（{@code NFR-12} 精神）。 */
    @Override
    public String toString() {
        return "ShopUser{id='" + id + "', account='" + account
                + "', createAt=" + createAt + ", updateAt=" + updateAt + '}';
    }
}
