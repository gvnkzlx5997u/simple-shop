package com.simpleshop.intention.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;

public interface IntentionRepository extends JpaRepository<Intention, String> {

    Page<Intention> findByBuyer_IdAndStatusInOrderByCreateAtDesc(
            String buyerId, Collection<IntentionStatus> statuses, Pageable pageable);

    List<Intention> findByGoods_IdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    Optional<Intention> findFirstByGoods_IdAndStatusOrderByQueueOrderAsc(
            String goodsId, IntentionStatus status);

    long countByGoods_IdAndStatusIn(String goodsId, Collection<IntentionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intention i where i.id = :id")
    Optional<Intention> findWithLockById(@Param("id") String id);
}
