package com.simpleshop.account.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 卖家账号。系统只有一个账号，行数由种子数据和账号服务保证。
 */
@Entity
@Table(name = "simpleshop_seller_account")
public class SellerAccount {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String account;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    protected SellerAccount() {
    }

    public static SellerAccount create(String account, String passwordHash) {
        SellerAccount value = new SellerAccount();
        value.id = Ids.next("SA");
        value.account = account;
        value.password = passwordHash;
        return value;
    }

    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        if (createAt == null) {
            createAt = now;
        }
        if (updateAt == null) {
            updateAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updateAt = DatabaseTimeProvider.utcNow();
    }

    public String getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
