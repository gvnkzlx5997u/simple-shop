package com.simpleshop.payment.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.payment.entity.PaymentRefund;

/** 退款流水仓储：只按订单读，不分页；合计供订单详情聚合「已退款」。 */
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, String> {

    List<PaymentRefund> findByOrderIdOrderByRefundedAtDesc(String orderId);

    boolean existsByOrderIdAndReason(String orderId, String reason);

    Optional<PaymentRefund> findByOrderIdAndReason(String orderId, String reason);

    @Query("""
            select coalesce(sum(r.amount), 0) from PaymentRefund r
            where r.orderId = :orderId
            """)
    BigDecimal sumAmountByOrderId(@Param("orderId") String orderId);
}
