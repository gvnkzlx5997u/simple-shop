package com.simpleshop.category.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 二级类别；商品必须挂在二级类别上。
 */
@Entity
@Table(name = "simpleshop_category_l2",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_category_l2_parent_name", columnNames = {"fk_l1_id", "name"}))
public class CategoryL2 {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_l1_id", nullable = false)
    private CategoryL1 l1;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    private LocalDateTime updateAt;

    protected CategoryL2() {
    }

    public static CategoryL2 create(CategoryL1 l1, String name) {
        CategoryL2 value = new CategoryL2();
        value.id = Ids.next("C2");
        value.l1 = l1;
        value.name = name;
        return value;
    }

    public void rename(String name) {
        this.name = name;
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

    public CategoryL1 getL1() {
        return l1;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
