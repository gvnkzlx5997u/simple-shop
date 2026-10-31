package com.simpleshop.category.api;

/**
 * 类别删除前的商品引用检查：由 goods 实现，避免 category 反向依赖 goods。
 */
public interface CategoryGoodsReferencePort {

    boolean existsByCategoryId(String categoryL2Id);
}
