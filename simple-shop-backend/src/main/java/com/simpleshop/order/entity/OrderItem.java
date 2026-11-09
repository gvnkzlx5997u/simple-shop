package com.simpleshop.order.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.simpleshop.common.id.Ids;

/**
 * 订单项。只承载商品引用、下单快照与数量，没有状态：
 * 明细的有效性由整单状态派生，本期没有部分发货、部分取消或部分完成。
 *
 * <p>商品名与单价是下单时快照，商品后续改名、改价、下架都不回写这里。
 */
@Entity
@Table(name = "simpleshop_order_item")
public class OrderItem {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_order_id", nullable = false)
    private Order order;

    /** 商品 ID。商品行永不物理删除，引用长期有效，因此不引入级联删除。 */
    @Column(name = "fk_goods_id", nullable = false, length = 50)
    private String goodsId;

    @Column(name = "goods_name", nullable = false, length = 50)
    private String goodsName;

    @Column(name = "unit_price", nullable = false, precision = 8, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    static OrderItem snapshot(Order order, OrderLine line) {
        if (line == null) {
            throw new IllegalArgumentException("order line is required");
        }
        String goodsId = requireText(line.goodsId(), "goods id");
        String goodsName = requireText(line.goodsName(), "goods name");
        BigDecimal unitPrice = requirePrice(line.unitPrice());
        if (line.quantity() < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }

        OrderItem item = new OrderItem();
        item.id = Ids.next("OI");
        item.order = order;
        item.goodsId = goodsId;
        item.goodsName = goodsName;
        item.unitPrice = unitPrice;
        item.quantity = line.quantity();
        return item;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("unit price must be positive");
        }
        // 金额全链路只有两位小数：商品价 decimal(8,2)，这里提前拒绝多余精度，
        // 避免实体值被数据库静默舍入后与总额对不上。
        if (price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("unit price must have at most 2 decimals");
        }
        return price.setScale(2);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
