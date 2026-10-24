package com.simpleshop.history.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.history.entity.GoodsHistory;

public interface GoodsHistoryRepository extends JpaRepository<GoodsHistory, String> {

    /** 卖家历史列表；归档时间秒级精度，用 id 做 tie-breaker。 */
    Page<GoodsHistory> findAllByOrderByArchivedAtDescIdDesc(Pageable pageable);
}
