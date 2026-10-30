package com.simpleshop.inventory.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.inventory.entity.GoodsStock;

public interface GoodsStockRepository extends JpaRepository<GoodsStock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GoodsStock s where s.goodsId = :goodsId")
    Optional<GoodsStock> findWithLockByGoodsId(@Param("goodsId") String goodsId);

    List<GoodsStock> findByGoodsIdIn(Collection<String> goodsIds);
}
