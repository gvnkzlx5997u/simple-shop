package com.simpleshop.history.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.history.entity.IntentionHistory;

/**
 * 历史读模型：当前表已终态意向与历史意向表的并集。
 * 分页必须作用在 UNION 之后；create_at 秒级精度，用 id 做 tie-breaker。
 */
public interface HistoryQueryRepository extends JpaRepository<IntentionHistory, String> {

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
