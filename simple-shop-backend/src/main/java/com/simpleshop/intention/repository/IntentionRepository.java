package com.simpleshop.intention.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;

public interface IntentionRepository extends JpaRepository<Intention, String> {

    Page<Intention> findByBuyer_IdAndStatusInOrderByCreateAtDesc(
            String buyerId, Collection<IntentionStatus> statuses, Pageable pageable);

    List<Intention> findByGoods_IdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    List<Intention> findByGoods_IdOrderByQueueOrderAsc(String goodsId);

    Optional<Intention> findFirstByGoods_IdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    long countByGoods_IdAndStatusIn(String goodsId, Collection<IntentionStatus> statuses);

    /**
     * 直接删除该商品全部当前意向行，绕过一级缓存。归档流程专用：
     * 必须在写完历史意向且删除商品行之前执行；删除后不得再访问当前意向实体。
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Intention i where i.goods.id = :goodsId")
    void deleteRowByGoodsId(@Param("goodsId") String goodsId);

    /** 卖家意向名单：按队列序号升序分页；EntityGraph 避免逐条加载买家。 */
    @EntityGraph(attributePaths = {"buyer"})
    Page<Intention> findByGoods_IdAndStatusInOrderByQueueOrderAsc(
            String goodsId, Collection<IntentionStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intention i where i.id = :id")
    Optional<Intention> findWithLockById(@Param("id") String id);

    /**
     * 买家历史分页：当前表已终态意向 ∪ 历史意向表。
     * 分页必须作用在 UNION 之后；create_at 秒级精度，用 id 做 tie-breaker。
     * UNION 读模型归 intention；历史表只出现在这条只读 SQL 中。
     */
    @Query(value = """
            select i.id as id, g.name as goods_name, i.create_at as create_at,
                   i.status as result, i.finish_at as finish_at
            from simpleshop_intention i
            join simpleshop_goods g on g.id = i.fk_goods_id
            where i.fk_buyer_id = :buyerId and i.status in ('SUCCEEDED', 'FAILED')
            union all
            select ih.id as id, ih.goods_name as goods_name, ih.create_at as create_at,
                   ih.result as result, ih.finish_at as finish_at
            from simpleshop_intention_history ih
            where ih.buyer_id = :buyerId
            order by create_at desc, id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<BuyerHistoryRow> findBuyerHistoryPage(
            @Param("buyerId") String buyerId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
            select count(*) from (
                select i.id from simpleshop_intention i
                where i.fk_buyer_id = :buyerId and i.status in ('SUCCEEDED', 'FAILED')
                union all
                select ih.id from simpleshop_intention_history ih
                where ih.buyer_id = :buyerId
            ) merged
            """, nativeQuery = true)
    long countBuyerHistory(@Param("buyerId") String buyerId);

    /**
     * 卖家客户购买历史分页：与买家历史同一并集，多出商品 ID 与失败原因。
     * 总数与买家历史口径相同，复用 countBuyerHistory。
     */
    @Query(value = """
            select i.id as id, g.id as goods_id, g.name as goods_name,
                   i.create_at as create_at, i.status as result,
                   i.fail_type as fail_type, i.finish_at as finish_at
            from simpleshop_intention i
            join simpleshop_goods g on g.id = i.fk_goods_id
            where i.fk_buyer_id = :buyerId and i.status in ('SUCCEEDED', 'FAILED')
            union all
            select ih.id as id, ih.fk_goods_history_id as goods_id, ih.goods_name as goods_name,
                   ih.create_at as create_at, ih.result as result,
                   ih.fail_type as fail_type, ih.finish_at as finish_at
            from simpleshop_intention_history ih
            where ih.buyer_id = :buyerId
            order by create_at desc, id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<CustomerPurchaseRow> findCustomerPurchasePage(
            @Param("buyerId") String buyerId,
            @Param("limit") int limit,
            @Param("offset") long offset);
}
