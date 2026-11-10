package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.inventory.api.StockPort;

/**
 * 商品只读端口实现：一次性取到类别链、主图与库存，避免调用方感知实体与懒加载。
 */
@Component
public class GoodsQueryAdapter implements GoodsQueryPort {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImages;
    private final StockPort stockPort;

    public GoodsQueryAdapter(
            GoodsRepository goodsRepository,
            GoodsImageRepository goodsImages,
            StockPort stockPort) {
        this.goodsRepository = goodsRepository;
        this.goodsImages = goodsImages;
        this.stockPort = stockPort;
    }

    @Override
    public GoodsSnapshot require(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        Goods goods = goodsRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        String mainImageUrl = goodsImages.findMainByGoodsId(id)
                .map(GoodsImage::getUrl)
                .orElse(null);
        return snapshot(goods, mainImageUrl, stockPort.availableOf(id));
    }

    @Override
    public Map<String, GoodsSnapshot> snapshotsOf(Collection<String> goodsIds) {
        List<String> ids = normalizedSorted(goodsIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Goods> goodsList = goodsRepository.findDetailsByIdIn(ids);
        if (goodsList.isEmpty()) {
            return Map.of();
        }
        Map<String, String> mainImages = mainImagesOf(ids);
        Map<String, Integer> stocks = stockPort.availableAll(ids);
        Map<String, GoodsSnapshot> result = new HashMap<>();
        for (Goods goods : goodsList) {
            String id = goods.getId();
            result.put(id, snapshot(goods, mainImages.get(id), stocks.getOrDefault(id, 0)));
        }
        return Map.copyOf(result);
    }

    @Override
    public List<GoodsSnapshot> lockForOrder(Collection<String> goodsIds) {
        List<String> ids = normalizedSorted(goodsIds);
        if (ids.isEmpty()) {
            return List.of();
        }

        // 逐行加锁而不是一条 IN 查询：IN 列表的加锁顺序由执行计划决定，
        // 逐行按 ID 升序加锁才能保证两笔交叉下单不会互相等待成环。
        //
        // 这里刻意不给查询加 EntityGraph：带写锁的 join fetch 会把锁范围扩到类别行上，
        // 同一类别下不同商品的两笔下单反而可能互相等待。类别链的懒加载代价接受。
        List<Goods> locked = new ArrayList<>(ids.size());
        for (String id : ids) {
            goodsRepository.findWithLockById(id).ifPresent(locked::add);
        }

        // 只回商品自身字段：主图与库存都不查，原因不是省查询，而是**不能查**。
        // 成交路径紧接着要在库存行上加锁扣减；若这里先把库存实体读进持久化上下文，
        // 后面的加锁查询会拿一级缓存里的旧数量去校验，两笔订单会同时通过 → 超卖。
        // 已由 OrderConcurrencyTest 复现并验证。调用方要库存请另取 StockPort 实时值。
        List<GoodsSnapshot> snapshots = new ArrayList<>(locked.size());
        for (Goods goods : locked) {
            snapshots.add(snapshot(goods, null, 0));
        }
        return snapshots;
    }

    /**
     * 去空、去重、按 ID 升序。
     *
     * <p>批量读也用它，是为了让加锁路径与只读路径共用同一套归一化：排序对只读没有影响，
     * 但只留一份实现，不会出现「加锁路径忘了排序」这种改错地方的隐患。
     */
    private static List<String> normalizedSorted(Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return List.of();
        }
        TreeSet<String> ids = new TreeSet<>();
        for (String raw : goodsIds) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** 批量取主图：按商品分组后只保留 is_main 的那张，没有主图的商品不入映射。 */
    private Map<String, String> mainImagesOf(Collection<String> goodsIds) {
        List<GoodsImage> images = goodsImages.findByGoods_IdInOrderBySortNoAsc(goodsIds);
        Map<String, String> mainImages = new HashMap<>();
        for (GoodsImage image : images) {
            if (image.isMain()) {
                mainImages.putIfAbsent(image.getGoods().getId(), image.getUrl());
            }
        }
        return mainImages;
    }

    private static GoodsSnapshot snapshot(Goods goods, String mainImageUrl, int stock) {
        return new GoodsSnapshot(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                goods.getStatus(),
                goods.getCategory().getL1().getName(),
                goods.getCategory().getName(),
                goods.getCreateAt(),
                mainImageUrl,
                stock,
                goods.getOffShelfReason());
    }
}
