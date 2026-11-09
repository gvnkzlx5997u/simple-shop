package com.simpleshop.inventory.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.inventory.api.StockPort;
import com.simpleshop.inventory.api.StockShortage;
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
    public List<StockShortage> deductAll(Map<String, Integer> quantities) {
        Map<String, Integer> required = normalize(quantities);
        if (required.isEmpty()) {
            return List.of();
        }

        // 先按 ID 升序把库存行全部锁住，再统一校验、统一扣减：
        // 顺序固定才不会与另一笔成交交叉等待，也不会出现扣了一半才发现不够。
        Map<String, GoodsStock> locked = new HashMap<>(required.size());
        for (String goodsId : required.keySet()) {
            locked.put(goodsId, lock(goodsId));
        }

        List<StockShortage> shortages = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            GoodsStock stock = locked.get(entry.getKey());
            if (stock.getAvailable() < entry.getValue()) {
                shortages.add(new StockShortage(
                        entry.getKey(), entry.getValue(), stock.getAvailable()));
            }
        }
        if (!shortages.isEmpty()) {
            return List.copyOf(shortages);
        }

        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            locked.get(entry.getKey()).deduct(entry.getValue());
        }
        stocks.saveAllAndFlush(locked.values());
        return List.of();
    }

    /** 归一化数量表：去掉空 ID、按 ID 升序，数量必须为正。 */
    private static Map<String, Integer> normalize(Map<String, Integer> quantities) {
        Map<String, Integer> required = new TreeMap<>();
        if (quantities == null) {
            return required;
        }
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            String goodsId = normalize(entry.getKey());
            int quantity = entry.getValue() == null ? 0 : entry.getValue();
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be at least 1 for goods " + goodsId);
            }
            required.put(goodsId, quantity);
        }
        return required;
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
