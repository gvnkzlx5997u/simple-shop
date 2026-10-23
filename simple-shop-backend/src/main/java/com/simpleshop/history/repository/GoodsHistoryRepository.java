package com.simpleshop.history.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.history.entity.GoodsHistory;

public interface GoodsHistoryRepository extends JpaRepository<GoodsHistory, String> {

    Page<GoodsHistory> findAllByOrderByArchivedAtDesc(Pageable pageable);
}
