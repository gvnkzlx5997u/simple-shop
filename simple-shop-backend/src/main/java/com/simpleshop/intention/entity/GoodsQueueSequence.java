package com.simpleshop.intention.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 按商品维护的队列序号。一个商品一行，初值 0。
 */
@Entity
@Table(name = "simpleshop_goods_queue_sequence")
public class GoodsQueueSequence {

    @Id
    @Column(name = "fk_goods_id", length = 50)
    private String goodsId;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    protected GoodsQueueSequence() {
    }

    public static GoodsQueueSequence forGoods(String goodsId) {
        GoodsQueueSequence value = new GoodsQueueSequence();
        value.goodsId = goodsId;
        value.currentValue = 0;
        return value;
    }

    public long nextValue() {
        currentValue++;
        return currentValue;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public long getCurrentValue() {
        return currentValue;
    }
}
