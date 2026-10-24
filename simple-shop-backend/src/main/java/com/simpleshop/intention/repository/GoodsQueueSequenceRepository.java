package com.simpleshop.intention.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.intention.entity.GoodsQueueSequence;

public interface GoodsQueueSequenceRepository extends JpaRepository<GoodsQueueSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from GoodsQueueSequence q where q.goodsId = :goodsId")
    Optional<GoodsQueueSequence> findWithLockByGoodsId(@Param("goodsId") String goodsId);
}
