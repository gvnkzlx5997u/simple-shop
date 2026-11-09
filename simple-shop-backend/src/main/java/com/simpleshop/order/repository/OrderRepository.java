package com.simpleshop.order.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单仓储。列表查询一律按下单时间倒序并用 id 兜底，保证翻页稳定；
 * 排序写进查询而不依赖调用方传 Sort，避免同一时间戳的订单在页间抖动。
 *
 * <p>明细不在订单上加载：订单列表与详情只回订单头，明细由订单项仓储按订单 ID 单独查。
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderNo(String orderNo);

    /** 状态推进与完成扣减先锁订单行，重复推进与重复完成都在拿到锁后再判断。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findWithLockById(@Param("id") String id);

    /**
     * 订单列表的三种入口共用一条查询：买家「我的订单」只传 buyerId，卖家订单管理可任意组合，
     * 按商品查订单时经订单项反查订单 ID。参数为 null 表示该条件不参与筛选。
     */
    @Query(value = """
            select o from Order o
            where (:status is null or o.status = :status)
              and (:buyerId is null or o.buyerId = :buyerId)
              and (:goodsId is null or exists (
                    select i.id from OrderItem i where i.order = o and i.goodsId = :goodsId))
            order by o.createAt desc, o.id desc
            """,
            countQuery = """
            select count(o) from Order o
            where (:status is null or o.status = :status)
              and (:buyerId is null or o.buyerId = :buyerId)
              and (:goodsId is null or exists (
                    select i.id from OrderItem i where i.order = o and i.goodsId = :goodsId))
            """)
    Page<Order> search(
            @Param("status") OrderStatus status,
            @Param("buyerId") String buyerId,
            @Param("goodsId") String goodsId,
            Pageable pageable);

    /**
     * 引用该商品的未完成订单数。商品手动下架与手动把库存改成 0 之前用它判断能否执行；
     * 只读订单表、不加订单行锁，与下单的商品行锁配合即可挡住竞态。
     */
    @Query("""
            select count(i) from OrderItem i
            where i.goodsId = :goodsId and i.order.status in :statuses
            """)
    long countByGoodsIdAndOrderStatusIn(
            @Param("goodsId") String goodsId,
            @Param("statuses") Collection<OrderStatus> statuses);

    /**
     * 当日订单号已用的最大序列。订单号由「前缀 + 日期 + 补零序列」构成，
     * 取后缀的数值最大值而不是字符串最大值，序列超过补零位数时也不会算错。
     */
    @Query(value = """
            select coalesce(max(cast(substring(order_no, char_length(:prefix) + 1) as unsigned)), 0)
            from simpleshop_order
            where order_no like concat(:prefix, '%')
            """,
            nativeQuery = true)
    long maxSequenceOfDay(@Param("prefix") String prefix);
}
