package com.simpleshop.goods.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 商品只读查询端口：由 goods 实现，供订单、购物车、收藏取商品快照，调用方不感知实体与懒加载。
 *
 * <p>调用方只能拿到 {@link GoodsSnapshot}。三个方法按用途分开，不要互相替代：单件操作走
 * {@link #require(String)}；列表展示走 {@link #snapshotsOf(Collection)}（不加锁）；下单与成交
 * 这类需要与并发下架互斥的写路径走 {@link #lockForOrder(Collection)}（加写锁）。把只读查询
 * 当加锁查询用，会让列表页莫名其妙地给每行上写锁。
 */
public interface GoodsQueryPort {

    GoodsSnapshot require(String goodsId);

    /**
     * 批量取快照，返回以商品 ID 为键的映射；入参里的空值与重复值会被忽略，不存在的商品
     * 不出现在结果中。
     *
     * <p>返回映射而不是列表：购物车、收藏这类列表要按行取对应商品，逐行 {@code get} 比先
     * 转成映射更直接。调用方不要依赖其迭代顺序。
     *
     * <p>本方法不加锁，只用于读。列表类场景必须走本方法一次取回，不要循环调用
     * {@link #require(String)}；写路径若需保证「取到快照后商品不会被并发下架」，用
     * {@link #lockForOrder(Collection)}，不能改用本方法。
     */
    Map<String, GoodsSnapshot> snapshotsOf(Collection<String> goodsIds);

    /**
     * 下单前按商品行加写锁并取下单快照。
     *
     * <p>加锁顺序固定为商品 ID 升序：两笔订单交叉下单时不会互相等待成环。
     * 不存在的商品不出现在返回列表里，由调用方判定为「商品不存在」。
     *
     * <p>返回列表而不是映射，是为了让取快照的顺序与加锁顺序一致；要按 ID 取值请自行转成
     * 映射，不要假设列表里一定有某个商品。
     *
     * <p>返回的快照**只保证商品自身字段**：{@code mainImageUrl} 恒为 {@code null}、
     * {@code stock} 恒为 {@code 0}。这不是实现偷懒——成交路径紧接着要在库存行上加写锁扣减，
     * 若本方法先把库存实体读进持久化上下文，后面的加锁查询会拿缓存里的旧数量校验，两笔订单
     * 会同时通过而超卖。下单要判断库存，请另取库存聚合的实时值。
     */
    List<GoodsSnapshot> lockForOrder(Collection<String> goodsIds);
}
