package com.simpleshop.aftersale.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.aftersale.entity.Aftersale;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;

/**
 * 售后单仓储。
 *
 * <p>列表只有一条查询：买家侧固定带 buyerId，卖家侧传空表示全部；状态与订单都是可选筛选，
 * 参数为 null 时不参与条件。排序固定按申请时间倒序并用 id 兜底，保证翻页稳定。
 */
public interface AftersaleRepository extends JpaRepository<Aftersale, String> {

    @Query(value = """
            select a from Aftersale a
            where (:buyerId is null or a.buyerId = :buyerId)
              and (:status is null or a.status = :status)
              and (:orderId is null or a.orderId = :orderId)
            order by a.createAt desc, a.id desc
            """,
            countQuery = """
            select count(a) from Aftersale a
            where (:buyerId is null or a.buyerId = :buyerId)
              and (:status is null or a.status = :status)
              and (:orderId is null or a.orderId = :orderId)
            """)
    Page<Aftersale> search(
            @Param("buyerId") String buyerId,
            @Param("status") AftersaleStatus status,
            @Param("orderId") String orderId,
            Pageable pageable);

    Optional<Aftersale> findByIdAndBuyerId(String id, String buyerId);

    /** 完成并退款前先锁售后单，避免同一张单被两次提交同时推进。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aftersale a where a.id = :id")
    Optional<Aftersale> findWithLockById(@Param("id") String id);

    /**
     * 该订单项已产生退款的售后单数。完成并退款前用它判额度；
     * 调用方必须先锁住订单项行，否则两个并发请求会同时读到 0。
     */
    long countByOrderItemIdAndStatusAndResultIn(
            String orderItemId, AftersaleStatus status, Collection<AftersaleResult> results);

    long countByOrderId(String orderId);

    /** 订单维度的退款合计：只统计已完成的退款类售后，换货与已取消不计。 */
    @Query("""
            select coalesce(sum(a.refundAmount), 0) from Aftersale a
            where a.orderId = :orderId and a.status = :status and a.result in :results
            """)
    BigDecimal sumRefundByOrderId(
            @Param("orderId") String orderId,
            @Param("status") AftersaleStatus status,
            @Param("results") Collection<AftersaleResult> results);

    /** 售后单号取每日最大序列，与订单号同一套「前缀 + 日期 + 补零序列」思路。 */
    @Query(value = """
            select coalesce(max(cast(substring(aftersale_no, char_length(:prefix) + 1) as unsigned)), 0)
            from simpleshop_aftersale
            where aftersale_no like concat(:prefix, '%')
            """,
            nativeQuery = true)
    long maxSequenceOfDay(@Param("prefix") String prefix);
}
