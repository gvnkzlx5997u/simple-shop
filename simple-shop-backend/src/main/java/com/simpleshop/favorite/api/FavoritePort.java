package com.simpleshop.favorite.api;

import java.util.Collection;

/**
 * 收藏写入端口：由 favorite 实现，供 cart 的「转收藏」使用。
 *
 * <p>跨功能调用只允许走对方的 api 包，cart 不得直接依赖 favorite 的 service / entity / repository。
 */
public interface FavoritePort {

    /**
     * 幂等批量写入收藏：已存在的商品跳过，不加行也不改收藏时间。
     *
     * <p>必须在调用方的事务内执行，由调用方决定提交或回滚。
     *
     * @return 真正新增的条数
     */
    int addAll(String buyerId, Collection<String> goodsIds);
}
