package com.simpleshop.cart.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.cart.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, String> {

    /** 购物车列表：最新加购的在前，与索引 idx_cart_item_buyer_create 一致。 */
    List<CartItem> findByBuyerIdOrderByCreateAtDescIdDesc(String buyerId);

    List<CartItem> findByBuyerIdAndGoodsIdIn(String buyerId, Collection<String> goodsIds);

    boolean existsByBuyerIdAndGoodsId(String buyerId, String goodsId);

    /**
     * 按「买家 + 商品」删除，返回删除行数。
     *
     * <p>用一条批量 delete 而不是派生删除：派生删除会先 select 出实体再逐条 remove，
     * 两个请求同时删同一行时，后一个在 flush 阶段发现行已被删掉，Hibernate 会抛
     * {@code ObjectOptimisticLockingFailureException} —— 串行时幂等、并发时 500。
     * 一条 delete 直接返回影响行数，删不到就是 0，才是真的幂等。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.buyerId = :buyerId and c.goodsId = :goodsId")
    int deleteByBuyerIdAndGoodsId(
            @Param("buyerId") String buyerId, @Param("goodsId") String goodsId);

    /** 批量版，语义同上。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.buyerId = :buyerId and c.goodsId in :goodsIds")
    int deleteByBuyerIdAndGoodsIdIn(
            @Param("buyerId") String buyerId, @Param("goodsIds") Collection<String> goodsIds);

    /**
     * 幂等加购：唯一键冲突时原地不动，不加行也不改加购时间。
     *
     * <p>不用「先查再插」：并发下双方都查不到、都插入，唯一键会让其中一个抛异常，
     * 而异常进入事务即标记回滚，吞掉异常也救不回来。
     *
     * <p>返回值的行数<b>不能</b>用来判断是否新增：MySQL Connector/J 默认
     * {@code useAffectedRows=false}，冲突时返回的是「找到的行数」(1) 而不是「影响的行数」(0)。
     * 需要区分「新增 / 已存在」时，先用 {@link #existsByBuyerIdAndGoodsId} 判断。
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into simpleshop_cart_item (id, fk_buyer_id, fk_goods_id, create_at)
            values (:id, :buyerId, :goodsId, :createAt)
            on duplicate key update fk_goods_id = fk_goods_id
            """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") String id,
            @Param("buyerId") String buyerId,
            @Param("goodsId") String goodsId,
            @Param("createAt") LocalDateTime createAt);
}
