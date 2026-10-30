package com.simpleshop.intention.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.intention.entity.GoodsQueueSequence;

public interface GoodsQueueSequenceRepository extends JpaRepository<GoodsQueueSequence, String> {

    /** 惰性建行：发布不再跨模块写队列表；ON DUPLICATE 以排他锁保证并发首提交串行。 */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into simpleshop_goods_queue_sequence "
            + "(fk_goods_id, current_value) values (:goodsId, 0) "
            + "on duplicate key update current_value = current_value", nativeQuery = true)
    void createIfAbsent(@Param("goodsId") String goodsId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from GoodsQueueSequence q where q.goodsId = :goodsId")
    Optional<GoodsQueueSequence> findWithLockByGoodsId(@Param("goodsId") String goodsId);
}
