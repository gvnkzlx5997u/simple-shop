package com.simpleshop.goods.service;

import org.springframework.stereotype.Component;

import com.simpleshop.category.service.CategoryGoodsReferencePort;
import com.simpleshop.goods.repository.GoodsRepository;

/**
 * 类别删除前的商品引用检查，由 goods 功能实现。
 */
@Component
public class GoodsCategoryReferenceAdapter implements CategoryGoodsReferencePort {

    private final GoodsRepository goods;

    public GoodsCategoryReferenceAdapter(GoodsRepository goods) {
        this.goods = goods;
    }

    @Override
    public boolean existsByCategoryId(String categoryL2Id) {
        return categoryL2Id != null && goods.existsByCategory_Id(categoryL2Id);
    }
}
