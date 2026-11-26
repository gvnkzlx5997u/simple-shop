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
import com.simpleshop.common.text.TextRules;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 订单头。整单流转，状态只按序前进；订单项没有独立状态。
 *
 * <p>主键是 {@code O + UUID}，{@code order_no} 只是给人读的业务编号，不承担主键职责：
 * 主键用 UUID 是为了不在主键里编码业务含义，订单号则是为了人能念出来，
 * 两者唯一性来源不同，不要互相替代。订单号由唯一索引兜底，撞号时由调用方整事务重试。
 *
 * <p>明细与总额在 {@link #place} 时一次成型，之后不再修改：商品清单、单价、数量、
 * 总额都不可变，商品后续下架或改价不影响历史订单。收货信息同理，存的是下单那一刻的快照，
 * 地址簿之后怎么改都影响不到已下的订单。
 *
 * <p>状态字段只能经本类的方法变化：每个动作先断言当前状态，再由调用方先做业务拒绝。
 * 这样即使有人绕过服务层直接改实体，也推不出「跳级」或「回退」的订单。
 */
@Entity
@Table(name = "simpleshop_order")
public class Order {

    private static final int ORDER_NO_MAX_LENGTH = 32;
    private static final int CARRIER_MAX_LENGTH = 50;
    private static final int TRACKING_NO_MAX_LENGTH = 500;
    private static final int LOGISTICS_REMARK_MAX_LENGTH = 500;

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

    /** 收货人、手机号、详细地址各存一份快照，不引用地址簿。 */
    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "receiver_address", nullable = false, length = 200)
    private String receiverAddress;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /**
     * 支付渠道与支付时间成对出现：未支付时都为空，付款后都有值。
     * 退款要原路退回，所以渠道必须留在订单上，不能只记在支付流水里。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_channel", length = 20)
    private PaymentChannel paymentChannel;

    @Column(name = "paid_at")
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * 物流三项只在发货时写入，发货后允许卖家修正。单号是多行文本：
     * 一个订单可能拆成多个包裹，本期不建包裹表，多个单号直接写在同一个字段里。
     */
    @Column(length = CARRIER_MAX_LENGTH)
    private String carrier;

    @Column(name = "tracking_no", length = TRACKING_NO_MAX_LENGTH)
    private String trackingNo;

    @Column(name = "logistics_remark", length = LOGISTICS_REMARK_MAX_LENGTH)
    private String logisticsRemark;

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
     * 下单：一次接收收货快照与全部明细，校验通过后计算总额。
     *
     * <p>同一订单不允许出现两行同商品，数据库唯一键是最后的兜底；这里提前拒绝，
     * 让错误停在构造阶段而不是提交时才变成约束异常。是否需要把重复明细合并成一行
     * 由下单服务决定，实体只接受已合并的结果。
     *
     * <p>下单不碰库存：不预占、不锁定、不扣减，库存只在买家确认收货时变化。
     */
    public static Order place(
            String orderNo, String buyerId, ReceiverSnapshot receiver, List<OrderLine> lines) {
        String number = requireOrderNo(orderNo);
        String buyer = requireText(buyerId, "buyer id");
        Objects.requireNonNull(receiver, "receiver");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one line");
        }

        Order order = new Order();
        order.id = Ids.next("O");
        order.orderNo = number;
        order.buyerId = buyer;
        order.receiverName = receiver.name();
        order.receiverPhone = receiver.phone();
        order.receiverAddress = receiver.address();
        order.status = OrderStatus.PENDING_PAYMENT;
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

    /**
     * 单行物流文本：承运商名称与备注都可能带换行，只有单号允许换行（多包裹）。
     * 这里只拒绝会破坏日志与响应的控制字符，不做格式校验——承运商是自由文本。
     */
    private static String requireLogisticsText(String raw, int maxLength, String field) {
        String value = requireText(raw, field);
        if (TextRules.charCount(value) > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
        if (TextRules.containsDisallowedControlChar(value)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return value;
    }

    private static String optionalLogisticsText(String raw, int maxLength, String field) {
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (TextRules.charCount(value) > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
        if (TextRules.containsDisallowedControlChar(value)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return value;
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

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public PaymentChannel getPaymentChannel() {
        return paymentChannel;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public String getLogisticsRemark() {
        return logisticsRemark;
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

    /**
     * 是否已付款。退款与否看这个事实，不按状态枚举倒推：
     * 已发货也会被取消，那时状态已经不是「已支付」，但钱确实收过。
     */
    public boolean isPaid() {
        return paidAt != null;
    }

    /**
     * 支付守门只看状态；24 小时时限是否已过由服务层用当前时间判断，
     * 实体不持有配置，也不自己读时钟。
     */
    public boolean canPay() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean canConfirm() {
        return status == OrderStatus.PAID;
    }

    public boolean canPrepare() {
        return status == OrderStatus.CONFIRMED;
    }

    public boolean canShip() {
        return status == OrderStatus.PREPARED;
    }

    /** 发货之后才谈得上改物流；交易完成仍可修正单号，已取消则不再允许。 */
    public boolean canUpdateLogistics() {
        return status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED;
    }

    /** 只有已发货的订单能确认收货；成交扣库存由服务层在同一事务里完成。 */
    public boolean canConfirmReceipt() {
        return status == OrderStatus.SHIPPED;
    }

    /** 买家在进入已发货之前可以取消；待支付取消不涉及退款。 */
    public boolean canCancelByBuyer() {
        return status == OrderStatus.PENDING_PAYMENT
                || status == OrderStatus.PAID
                || status == OrderStatus.CONFIRMED
                || status == OrderStatus.PREPARED;
    }

    /** 卖家在交易完成之前都能取消，含已发货。 */
    public boolean canCancelBySeller() {
        return canCancelByBuyer() || status == OrderStatus.SHIPPED;
    }

    /**
     * 支付成功：记录渠道与时间，进入已支付。
     *
     * <p>支付失败不调用本方法：订单停在待支付，买家可以重试，超时后由系统取消。
     */
    public void pay(PaymentChannel channel, LocalDateTime paidAt) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "pay");
        Objects.requireNonNull(channel, "payment channel");
        Objects.requireNonNull(paidAt, "paid at");
        this.paymentChannel = channel;
        this.paidAt = paidAt;
        this.status = OrderStatus.PAID;
    }

    /**
     * 卖家确认。状态判断由调用方先用 {@link #canConfirm()} 做业务拒绝，
     * 这里再断言一次，防止绕过服务层把订单推到非法状态。
     */
    public void confirm() {
        requireStatus(OrderStatus.PAID, "confirm");
        status = OrderStatus.CONFIRMED;
    }

    public void prepare() {
        requireStatus(OrderStatus.CONFIRMED, "prepare");
        status = OrderStatus.PREPARED;
    }

    /** 发货必须带承运商与单号：买家查物流靠这两项，缺一项就没有可查的东西。 */
    public void ship(String carrier, String trackingNo, String logisticsRemark) {
        requireStatus(OrderStatus.PREPARED, "ship");
        this.carrier = requireLogisticsText(carrier, CARRIER_MAX_LENGTH, "carrier");
        this.trackingNo = requireLogisticsText(
                trackingNo, TRACKING_NO_MAX_LENGTH, "tracking no");
        this.logisticsRemark = optionalLogisticsText(
                logisticsRemark, LOGISTICS_REMARK_MAX_LENGTH, "logistics remark");
        status = OrderStatus.SHIPPED;
    }

    /** 发货后修正物流信息：只改三项物流字段，订单状态不动。 */
    public void updateLogistics(String carrier, String trackingNo, String logisticsRemark) {
        if (!canUpdateLogistics()) {
            throw new IllegalStateException(
                    "cannot update logistics of an order in status " + status);
        }
        this.carrier = requireLogisticsText(carrier, CARRIER_MAX_LENGTH, "carrier");
        this.trackingNo = requireLogisticsText(
                trackingNo, TRACKING_NO_MAX_LENGTH, "tracking no");
        this.logisticsRemark = optionalLogisticsText(
                logisticsRemark, LOGISTICS_REMARK_MAX_LENGTH, "logistics remark");
    }

    /**
     * 买家确认收货：成交，进入交易完成，终态不可回退。
     *
     * <p>库存扣减不在这里做：实体不持有库存聚合，扣减在服务层的同一事务里先完成，
     * 失败会整单回滚，订单仍停在已发货。
     */
    public void confirmReceipt() {
        requireStatus(OrderStatus.SHIPPED, "confirm receipt");
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
