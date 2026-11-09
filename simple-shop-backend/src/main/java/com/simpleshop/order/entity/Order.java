package com.simpleshop.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单头。整单流转，状态只按序前进；订单项没有独立状态。
 *
 * <p>主键是 {@code O + UUID}，{@code order_no} 只是给人读的业务编号，不承担主键职责：
 * 主键用 UUID 是为了不在主键里编码业务含义，订单号则是为了人能念出来，
 * 两者唯一性来源不同，不要互相替代。订单号由唯一索引兜底，撞号时由调用方整事务重试。
 *
 * <p>明细与总额在 {@link #place} 时一次成型，之后不再修改：商品清单、单价、数量、
 * 总额都不可变，商品后续下架或改价不影响历史订单。
 */
@Entity
@Table(name = "simpleshop_order")
public class Order {

    private static final int ORDER_NO_MAX_LENGTH = 32;

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "order_no", nullable = false, unique = true, length = ORDER_NO_MAX_LENGTH)
    private String orderNo;

    /**
     * 下单买家账号 ID。订单包不引用账号实体，只在库层保留外键，
     * 展示所需的用户名由调用方另行读账号。
     */
    @Column(name = "fk_buyer_id", nullable = false, length = 50)
    private String buyerId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    /**
     * 下单：一次接收全部明细，校验通过后计算总额。
     *
     * <p>同一订单不允许出现两行同商品，数据库唯一键是最后的兜底；这里提前拒绝，
     * 让错误停在构造阶段而不是提交时才变成约束异常。是否需要把重复明细合并成一行
     * 由下单服务决定，实体只接受已合并的结果。
     */
    public static Order place(String orderNo, String buyerId, List<OrderLine> lines) {
        String number = requireOrderNo(orderNo);
        String buyer = requireText(buyerId, "buyer id");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one line");
        }

        Order order = new Order();
        order.id = Ids.next("O");
        order.orderNo = number;
        order.buyerId = buyer;
        order.status = OrderStatus.PENDING_CONFIRM;
        order.totalAmount = BigDecimal.ZERO.setScale(2);

        Set<String> seen = new HashSet<>();
        for (OrderLine line : lines) {
            OrderItem item = OrderItem.snapshot(order, line);
            if (!seen.add(item.getGoodsId())) {
                throw new IllegalArgumentException(
                        "duplicate goods in one order: " + item.getGoodsId());
            }
            order.items.add(item);
            order.totalAmount = order.totalAmount.add(
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return order;
    }

    private static String requireOrderNo(String orderNo) {
        String value = requireText(orderNo, "order no");
        if (value.length() > ORDER_NO_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "order no must not exceed " + ORDER_NO_MAX_LENGTH + " characters");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
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

    public String getOrderNo() {
        return orderNo;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    /** 明细只读：订单创建后清单不再变化，调用方不能借集合修改绕过状态口径。 */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean belongsTo(String candidateBuyerId) {
        return Objects.equals(buyerId, candidateBuyerId);
    }

    public boolean isUnfinished() {
        return status.isUnfinished();
    }

    public boolean canConfirm() {
        return status == OrderStatus.PENDING_CONFIRM;
    }

    public boolean canPrepare() {
        return status == OrderStatus.CONFIRMED;
    }

    public boolean canShip() {
        return status == OrderStatus.PREPARED;
    }

    /** 只有已发货的订单能完成；完成要同时扣库存，由服务层在同一事务里保证。 */
    public boolean canComplete() {
        return status == OrderStatus.SHIPPED;
    }

    /** 买家只能在进入已发货之前取消。 */
    public boolean canCancelByBuyer() {
        return status == OrderStatus.PENDING_CONFIRM
                || status == OrderStatus.CONFIRMED
                || status == OrderStatus.PREPARED;
    }

    /** 卖家在交易完成之前都能取消，含已发货。 */
    public boolean canCancelBySeller() {
        return status.isUnfinished();
    }

    /**
     * 卖家确认。状态判断由调用方先用 {@link #canConfirm()} 做业务拒绝，
     * 这里再断言一次，防止绕过服务层把订单推到非法状态。
     */
    public void confirm() {
        requireStatus(OrderStatus.PENDING_CONFIRM, "confirm");
        status = OrderStatus.CONFIRMED;
    }

    public void prepare() {
        requireStatus(OrderStatus.CONFIRMED, "prepare");
        status = OrderStatus.PREPARED;
    }

    public void ship() {
        requireStatus(OrderStatus.PREPARED, "ship");
        status = OrderStatus.SHIPPED;
    }

    /** 交易完成：只在已发货且库存已扣减成功时调用，终态，不可回退。 */
    public void complete() {
        requireStatus(OrderStatus.SHIPPED, "complete");
        status = OrderStatus.COMPLETED;
    }

    public void cancelByBuyer() {
        if (!canCancelByBuyer()) {
            throw new IllegalStateException("buyer cannot cancel an order in status " + status);
        }
        status = OrderStatus.CANCELED;
    }

    public void cancelBySeller() {
        if (!canCancelBySeller()) {
            throw new IllegalStateException("seller cannot cancel an order in status " + status);
        }
        status = OrderStatus.CANCELED;
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "cannot " + action + " an order in status " + status);
        }
    }
}
