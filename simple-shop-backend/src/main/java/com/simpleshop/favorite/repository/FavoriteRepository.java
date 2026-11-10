package com.simpleshop.favorite.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.favorite.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    /** 收藏列表：最近收藏的在前，与索引 idx_favorite_buyer_create 一致。 */
    Page<Favorite> findByBuyerIdOrderByCreateAtDescIdDesc(String buyerId, Pageable pageable);

    boolean existsByBuyerIdAndGoodsId(String buyerId, String goodsId);

    /**
     * 按「买家 + 商品」删除，返回删除行数。
     *
     * <p>与购物车同理用一条批量 delete：派生删除会先 select 再 remove，并发取消同一件时
     * 后一个会抛 {@code ObjectOptimisticLockingFailureException}，「取消幂等」在并发下就变成 500。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Favorite f where f.buyerId = :buyerId and f.goodsId = :goodsId")
    int deleteByBuyerIdAndGoodsId(
            @Param("buyerId") String buyerId, @Param("goodsId") String goodsId);

    /**
     * 幂等收藏：唯一键冲突时原地不动，不加行也不改收藏时间。
     *
     * <p>与购物车同理不用「先查再插」；返回值不能用来判断是否新增
     * （Connector/J 默认 {@code useAffectedRows=false}，冲突时返回找到的行数 1）。
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into simpleshop_favorite (id, fk_buyer_id, fk_goods_id, create_at)
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
