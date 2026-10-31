package com.simpleshop.goods.service;

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
