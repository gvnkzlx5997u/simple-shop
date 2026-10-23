package com.simpleshop.account.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 买家账号。用户名唯一，手机号允许重复。
 */
@Entity
@Table(name = "simpleshop_buyer_account")
public class BuyerAccount {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "default_location", length = 50)
    private String defaultLocation;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    protected BuyerAccount() {
    }

    public static BuyerAccount register(
            String username, String passwordHash, String phone, String defaultLocation) {
        BuyerAccount value = new BuyerAccount();
        value.id = Ids.next("BA");
        value.username = username;
        value.password = passwordHash;
        value.phone = phone;
        value.defaultLocation = defaultLocation;
        return value;
    }

    @PrePersist
    void prePersist() {
        if (createAt == null) {
            createAt = DatabaseTimeProvider.utcNow();
        }
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getDefaultLocation() {
        return defaultLocation;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}
