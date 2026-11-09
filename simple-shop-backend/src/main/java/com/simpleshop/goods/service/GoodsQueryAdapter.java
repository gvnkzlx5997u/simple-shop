package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.repository.GoodsRepository;

/**
 * 商品只读端口实现：一次性取到类别链，避免调用方感知实体与懒加载。
 */
@Component
public class GoodsQueryAdapter implements GoodsQueryPort {

    private final GoodsRepository goodsRepository;

    public GoodsQueryAdapter(GoodsRepository goodsRepository) {
        this.goodsRepository = goodsRepository;
    }

    @Override
    public GoodsSnapshot require(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        Goods goods = goodsRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        return snapshot(goods);
    }

    @Override
    public List<GoodsSnapshot> lockForOrder(Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = goodsIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .sorted()
                .toList();

        List<GoodsSnapshot> locked = new ArrayList<>(ids.size());
        for (String id : ids) {
            // 逐行加锁而不是一条 IN 查询：IN 列表的加锁顺序由执行计划决定，
            // 逐行按 ID 升序加锁才能保证两笔交叉下单不会互相等待成环。
            goodsRepository.findWithLockById(id)
                    .map(GoodsQueryAdapter::snapshot)
                    .ifPresent(locked::add);
        }
        return locked;
    }

    private static GoodsSnapshot snapshot(Goods goods) {
        return new GoodsSnapshot(
                goods.getId(),
                goods.getName(),
                goods.getPrice(),
                goods.getStatus(),
                goods.getCategory().getL1().getName(),
                goods.getCategory().getName(),
                goods.getCreateAt());
    }
}
