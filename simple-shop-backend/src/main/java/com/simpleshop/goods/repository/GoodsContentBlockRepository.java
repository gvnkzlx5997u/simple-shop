package com.simpleshop.goods.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.goods.entity.GoodsContentBlock;

public interface GoodsContentBlockRepository extends JpaRepository<GoodsContentBlock, String> {

    List<GoodsContentBlock> findByGoods_IdOrderBySortNoAsc(String goodsId);
}
