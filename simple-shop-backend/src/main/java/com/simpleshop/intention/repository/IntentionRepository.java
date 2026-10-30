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

    List<Intention> findByGoodsIdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    List<Intention> findByGoodsIdOrderByQueueOrderAsc(String goodsId);

    Optional<Intention> findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    long countByGoodsIdAndStatusIn(String goodsId, Collection<IntentionStatus> statuses);

    /**
     * 直接删除该商品全部当前意向行，绕过一级缓存。归档流程专用：
     * 必须在写完历史意向且删除商品行之前执行；删除后不得再访问当前意向实体。
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Intention i where i.goodsId = :goodsId")
    void deleteRowByGoodsId(@Param("goodsId") String goodsId);

    /** 卖家意向名单：按队列序号升序分页；EntityGraph 避免逐条加载买家。 */
    @EntityGraph(attributePaths = {"buyer"})
    Page<Intention> findByGoodsIdAndStatusInOrderByQueueOrderAsc(
            String goodsId, Collection<IntentionStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intention i where i.id = :id")
    Optional<Intention> findWithLockById(@Param("id") String id);

}
