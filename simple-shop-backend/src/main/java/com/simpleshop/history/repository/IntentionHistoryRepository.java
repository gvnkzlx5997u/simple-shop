package com.simpleshop.history.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.history.entity.IntentionHistory;

public interface IntentionHistoryRepository extends JpaRepository<IntentionHistory, String> {

    Page<IntentionHistory> findByBuyerIdOrderByCreateAtDesc(String buyerId, Pageable pageable);

    List<IntentionHistory> findByGoodsHistory_IdOrderByCreateAtAsc(String goodsHistoryId);
}
