package com.simpleshop.order.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.order.entity.OrderItem;

/**
 * 订单项仓储。订单项没有状态，只按订单读、按商品反查订单，以及给列表统计件数。
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    /**
     * 订单详情里的明细：按商品名排序并以下单快照兜底，保证同一订单多次请求顺序一致。
     * 明细数量很少，不分页。
     */
    List<OrderItem> findByOrder_IdOrderByGoodsNameAscIdAsc(String orderId);

    long countByOrder_Id(String orderId);

    /** 列表页一次取完多笔订单的件数，避免逐单 count。 */
    @Query("""
            select i.order.id as orderId, count(i) as itemCount
            from OrderItem i
            where i.order.id in :orderIds
            group by i.order.id
            """)
    List<ItemCount> countByOrderIds(@Param("orderIds") Collection<String> orderIds);

    /** 订单件数投影。 */
    interface ItemCount {

        String getOrderId();

        long getItemCount();
    }
}
