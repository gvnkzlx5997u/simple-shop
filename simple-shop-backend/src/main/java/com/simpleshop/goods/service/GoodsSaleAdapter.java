package com.simpleshop.goods.service;

import java.util.Collection;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.simpleshop.goods.api.GoodsSalePort;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.inventory.api.StockPort;

/**
 * 成交后的商品状态联动：扣到 0 的在售商品置为已售下架。
 *
 * <p>由本包判断「库存是否为 0」，订单只需要把本单涉及的商品交过来，
 * 不用关心库存聚合与商品状态之间的换算。
 */
@Component
public class GoodsSaleAdapter implements GoodsSalePort {

    private final GoodsRepository goodsRepository;
    private final StockPort stockPort;

    public GoodsSaleAdapter(GoodsRepository goodsRepository, StockPort stockPort) {
        this.goodsRepository = goodsRepository;
        this.stockPort = stockPort;
    }

    @Override
    public void markSoldOut(Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        TreeSet<String> ordered = new TreeSet<>();
        for (String goodsId : goodsIds) {
            if (goodsId != null && !goodsId.isBlank()) {
                ordered.add(goodsId.trim());
            }
        }
        for (String goodsId : ordered) {
            Goods goods = goodsRepository.findWithLockById(goodsId).orElse(null);
            if (goods == null || !goods.isOnSale()) {
                continue;
            }
            if (stockPort.availableOf(goodsId) > 0) {
                continue;
            }
            goods.offShelf(OffShelfReason.SOLD_OUT);
            goodsRepository.saveAndFlush(goods);
        }
    }
}
