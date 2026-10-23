package com.simpleshop.goods.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.goods.entity.GoodsImage;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, String> {

    List<GoodsImage> findByGoods_IdOrderBySortNoAsc(String goodsId);

    List<GoodsImage> findByGoods_IdInOrderBySortNoAsc(Collection<String> goodsIds);

    @Query("select i from GoodsImage i where i.goods.id = :goodsId and i.isMain = 1")
    Optional<GoodsImage> findMainByGoodsId(@Param("goodsId") String goodsId);
}
