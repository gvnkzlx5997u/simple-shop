package com.simpleshop.address.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.address.support.AddressRules;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 收货地址。字段只有三项：收货人、手机号、一行详细地址。
 *
 * <p>默认地址用 {@code is_default} 标记，库里有生成列 + 唯一索引保证每个买家最多一条默认，
 * 服务层切默认时必须是「先清旧的、再置新的」，顺序反了会被唯一键拒绝。
 *
 */
@Entity
@Table(name = "simpleshop_address")
public class Address {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "fk_buyer_id", nullable = false, length = 50)
    private String buyerId;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "detail_address", nullable = false, length = 200)
    private String detailAddress;

    @Column(name = "is_default", nullable = false)
    private boolean defaultFlag;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    protected Address() {
    }

    public static Address create(
            String buyerId,
            String receiverName,
            String receiverPhone,
            String detailAddress,
            boolean isDefault) {
        Address value = new Address();
        value.id = Ids.next("AD");
        value.buyerId = requireBuyerId(buyerId);
        value.receiverName = AddressRules.receiverName(receiverName);
        value.receiverPhone = AddressRules.receiverPhone(receiverPhone);
        value.detailAddress = AddressRules.detailAddress(detailAddress);
        value.defaultFlag = isDefault;
        return value;
    }

    /** 编辑只改三个字段；默认标记只能经「设为默认」变化，避免编辑表单顺手改掉默认。 */
    public void update(String receiverName, String receiverPhone, String detailAddress) {
        this.receiverName = AddressRules.receiverName(receiverName);
        this.receiverPhone = AddressRules.receiverPhone(receiverPhone);
        this.detailAddress = AddressRules.detailAddress(detailAddress);
    }

    public void markDefault() {
        defaultFlag = true;
    }

    public void clearDefault() {
        defaultFlag = false;
    }

    public boolean isDefault() {
        return defaultFlag;
    }

    public boolean belongsTo(String candidateBuyerId) {
        return Objects.equals(buyerId, candidateBuyerId);
    }

    private static String requireBuyerId(String buyerId) {
        if (buyerId == null || buyerId.isBlank()) {
            throw new IllegalArgumentException("buyer id is required");
        }
        return buyerId.trim();
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

    public String getBuyerId() {
        return buyerId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
