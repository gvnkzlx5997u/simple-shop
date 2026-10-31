package com.simpleshop.inventory.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.inventory.api.StockPort;
import com.simpleshop.inventory.entity.GoodsStock;
import com.simpleshop.inventory.repository.GoodsStockRepository;

/**
 * 库存端口实现：库存行独立加锁，商品行不参与库存读写。
 */
@Service
public class StockServiceImpl implements StockPort {

    private final GoodsStockRepository stocks;

    public StockServiceImpl(GoodsStockRepository stocks) {
        this.stocks = stocks;
    }

    @Override
    @Transactional
    public void initialize(String goodsId, int available) {
        stocks.saveAndFlush(GoodsStock.initialize(goodsId, available));
    }

    @Override
    @Transactional(readOnly = true)
    public int availableOf(String goodsId) {
        return require(goodsId).getAvailable();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> availableAll(Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return Map.of();
        }
        Collection<String> ids = new LinkedHashSet<>(goodsIds);
        List<GoodsStock> rows = stocks.findByGoodsIdIn(ids);
        Map<String, Integer> result = new HashMap<>(rows.size());
        for (GoodsStock row : rows) {
            result.put(row.getGoodsId(), row.getAvailable());
        }
        for (String id : ids) {
            if (!result.containsKey(id)) {
                throw new IllegalStateException("stock row missing for goods " + id);
            }
        }
        return Map.copyOf(result);
    }

    @Override
    @Transactional
    public void setAvailable(String goodsId, int available) {
        GoodsStock stock = lock(goodsId);
        stock.setAvailable(available);
        stocks.saveAndFlush(stock);
    }

    @Override
    @Transactional
    public int deductOne(String goodsId) {
        GoodsStock stock = lock(goodsId);
        if (stock.getAvailable() < 1) {
            throw new BusinessException(ErrorCode.STOCK_INVALID);
        }
        int remaining = stock.deductOne();
        stocks.saveAndFlush(stock);
        return remaining;
    }

    private GoodsStock require(String goodsId) {
        String id = normalize(goodsId);
        return stocks.findById(id)
                .orElseThrow(() -> new IllegalStateException("stock row missing for goods " + id));
    }

    private GoodsStock lock(String goodsId) {
        String id = normalize(goodsId);
        return stocks.findWithLockByGoodsId(id)
                .orElseThrow(() -> new IllegalStateException("stock row missing for goods " + id));
    }

    private static String normalize(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            throw new IllegalStateException("goods id is required for stock access");
        }
        return id;
    }
}
